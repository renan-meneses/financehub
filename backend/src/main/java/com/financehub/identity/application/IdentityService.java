package com.financehub.identity.application;

import com.financehub.identity.domain.*;
import com.financehub.identity.infrastructure.IdentityStore;
import com.financehub.shared.exceptions.ApiFailure;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final IdentityStore users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AccessTokens accessTokens;
    private final Clock clock;
    private final String publicUrl;
    private final String dummyHash;
    public IdentityService(IdentityStore users, JdbcTemplate jdbc, PasswordEncoder passwords,
                           AccessTokens accessTokens, Clock clock, @Value("${financehub.public-url}") String publicUrl) {
        this.users = users; this.jdbc = jdbc; this.passwords = passwords; this.accessTokens = accessTokens;
        this.clock = clock; this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.dummyHash = passwords.encode(IdentityPolicy.newToken());
    }
    @Transactional
    public void register(String email, String password, String displayName) {
        IdentityPolicy.validatePassword(password);
        var id = UUID.randomUUID();
        if (users.create(id, IdentityPolicy.normalizeEmail(email), passwords.encode(password), displayName.strip())) {
            challenge(id, IdentityPolicy.normalizeEmail(email), "VERIFY_EMAIL", Duration.ofHours(24));
            users.audit(id, null, "USER_REGISTERED", "USER", id);
        }
    }
    @Transactional
    public Optional<AuthResult> login(String email, String password, String device) {
        var candidate = users.byEmail(IdentityPolicy.normalizeEmail(email));
        boolean validLength = password.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwords.matches(validLength ? password : "invalid-password", candidate.map(UserAccount::passwordHash).orElse(dummyHash));
        if (candidate.isEmpty()) return Optional.empty();
        // All session/challenge mutations lock the user first, then recheck credentials and state.
        var user = users.lock(candidate.get().id()).orElseThrow(ApiFailure::unauthenticated);
        if (!validLength || !matches || !user.passwordHash().equals(candidate.get().passwordHash()) || !user.active()) {
            users.audit(user.id(), null, "LOGIN_FAILED", "USER", user.id());
            return Optional.empty();
        }
        UUID session = UUID.randomUUID();
        var expires = clock.instant().plus(Duration.ofDays(30));
        jdbc.update("INSERT INTO auth_sessions(id,user_id,device_name,created_at,expires_at) VALUES (?,?,?,?,?)",
            session, user.id(), device.strip(), now(), Timestamp.from(expires));
        users.audit(user.id(), null, "LOGIN_SUCCEEDED", "SESSION", session);
        return Optional.of(issue(user.id(), session, expires));
    }
    @Transactional
    public Optional<AuthResult> refresh(String raw) {
        if (!IdentityPolicy.validTokenShape(raw)) return Optional.empty();
        var tokens = jdbc.query("SELECT user_id FROM refresh_tokens WHERE token_hash=?",
            (rs,n) -> rs.getObject(1, UUID.class), IdentityPolicy.hash(raw));
        if (tokens.isEmpty()) return Optional.empty();
        var user = users.lock(tokens.getFirst()).orElseThrow(ApiFailure::unauthenticated);
        var rows = jdbc.query("""
            SELECT t.id,t.family_id,t.consumed_at,t.revoked_at,t.expires_at,s.revoked_at AS session_revoked,
                   s.expires_at AS session_expires FROM refresh_tokens t JOIN auth_sessions s ON s.id=t.family_id
            WHERE t.token_hash=?
            """, (rs,n) -> new RefreshState(rs.getObject("id",UUID.class), rs.getObject("family_id",UUID.class),
                rs.getTimestamp("consumed_at") != null, rs.getTimestamp("revoked_at") != null,
                rs.getTimestamp("session_revoked") != null, rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("session_expires").toInstant()), IdentityPolicy.hash(raw));
        if (rows.isEmpty()) return Optional.empty();
        var token = rows.getFirst();
        if (token.consumed()) {
            revoke(user.id(), token.session());
            users.audit(user.id(), null, "REFRESH_REUSE_DETECTED", "SESSION", token.session());
            // Returning normally commits revocation. The controller returns 401 after commit.
            return Optional.empty();
        }
        if (!user.active() || token.revoked() || token.sessionRevoked() || !token.expires().isAfter(clock.instant())
                || !token.sessionExpires().isAfter(clock.instant())) return Optional.empty();
        jdbc.update("UPDATE refresh_tokens SET consumed_at=? WHERE id=?", now(), token.id());
        users.audit(user.id(), null, "SESSION_REFRESHED", "SESSION", token.session());
        return Optional.of(issue(user.id(), token.session(), token.sessionExpires()));
    }
    @Transactional
    public void logout(String raw) {
        if (!IdentityPolicy.validTokenShape(raw)) return;
        var tokens = jdbc.query("SELECT user_id, family_id FROM refresh_tokens WHERE token_hash=?",
            (rs,n) -> new UUID[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)}, IdentityPolicy.hash(raw));
        if (!tokens.isEmpty()) {
            var token = tokens.getFirst();
            users.lock(token[0]).orElseThrow(ApiFailure::unauthenticated);
            revoke(token[0],token[1]);
            users.audit(token[0], null, "SESSION_LOGGED_OUT", "SESSION", token[1]);
        }
    }
    @Transactional
    public void requestChallenge(String email, String purpose) {
        users.byEmail(IdentityPolicy.normalizeEmail(email)).ifPresent(candidate -> {
            var user = users.lock(candidate.id()).orElseThrow(ApiFailure::unauthenticated);
            if (user.suspendedAt() == null && (purpose.equals("RESET_PASSWORD") || user.verifiedAt() == null))
                challenge(user.id(), user.email(), purpose, Duration.ofMinutes(30));
        });
    }
    @Transactional
    public void verifyEmail(String token) {
        UUID user = consumeChallenge(token, "VERIFY_EMAIL");
        jdbc.update("UPDATE users SET email_verified_at=COALESCE(email_verified_at,?),version=version+1 WHERE id=?", now(),user);
        users.audit(user,null,"EMAIL_VERIFIED","USER",user);
    }
    @Transactional
    public void resetPassword(String token, String password) {
        IdentityPolicy.validatePassword(password);
        UUID user = consumeChallenge(token,"RESET_PASSWORD");
        changeHash(user,password);
        users.audit(user,null,"PASSWORD_RESET","USER",user);
    }
    @Transactional
    public void changePassword(UUID id, String current, String replacement) {
        IdentityPolicy.validatePassword(replacement);
        var user = users.lock(id).orElseThrow(ApiFailure::unauthenticated);
        if (!user.active() || current.getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(current,user.passwordHash()))
            throw ApiFailure.unauthenticated();
        changeHash(id,replacement);
        users.audit(id,null,"PASSWORD_CHANGED","USER",id);
    }
    private void changeHash(UUID user, String password) {
        jdbc.update("UPDATE users SET password_hash=?,version=version+1 WHERE id=?",passwords.encode(password),user);
        jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",now(),user);
        jdbc.update("UPDATE refresh_tokens SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",now(),user);
        jdbc.update("UPDATE identity_challenges SET consumed_at=? WHERE user_id=? AND purpose='RESET_PASSWORD' AND consumed_at IS NULL",now(),user);
    }
    private UUID consumeChallenge(String token, String purpose) {
        if (!IdentityPolicy.validTokenShape(token)) throw ApiFailure.unauthenticated();
        var candidates = jdbc.query("SELECT user_id FROM identity_challenges WHERE token_hash=?", (rs,n) -> rs.getObject(1,UUID.class),IdentityPolicy.hash(token));
        if (candidates.isEmpty()) throw ApiFailure.unauthenticated();
        UUID user = candidates.getFirst();
        if (users.lock(user).orElseThrow(ApiFailure::unauthenticated).suspendedAt() != null) throw ApiFailure.unauthenticated();
        int changed = jdbc.update("""
            UPDATE identity_challenges SET consumed_at=? WHERE token_hash=? AND purpose=?
            AND consumed_at IS NULL AND expires_at>?
            """,now(),IdentityPolicy.hash(token),purpose,now());
        if (changed != 1) throw ApiFailure.unauthenticated();
        return user;
    }
    private void challenge(UUID user, String email, String purpose, Duration lifetime) {
        var raw = IdentityPolicy.newToken();
        var expiry = Timestamp.from(clock.instant().plus(lifetime));
        jdbc.update("UPDATE identity_challenges SET consumed_at=? WHERE user_id=? AND purpose=? AND consumed_at IS NULL", now(),user,purpose);
        jdbc.update("INSERT INTO identity_challenges(id,user_id,purpose,token_hash,expires_at,created_at) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(),user,purpose,IdentityPolicy.hash(raw),expiry,now());
        String route = purpose.equals("VERIFY_EMAIL") ? "verify-email" : "reset-password";
        String subject = purpose.equals("VERIFY_EMAIL") ? "Verify your FinanceHub email" : "Reset your FinanceHub password";
        String body = subject + "\n\n" + publicUrl + "/#!/" + route + "?token=" + raw
            + "\n\nIf you did not request this, ignore this message. This link expires at " + expiry.toInstant() + ".";
        jdbc.update("INSERT INTO email_outbox(id,recipient,subject,body,created_at,next_attempt_at,expires_at) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID(),email,subject,body,now(),now(),expiry);
    }
    private AuthResult issue(UUID user, UUID session, Instant expires) {
        var refresh = IdentityPolicy.newToken();
        jdbc.update("INSERT INTO refresh_tokens(id,user_id,family_id,token_hash,expires_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID(),user,session,IdentityPolicy.hash(refresh),Timestamp.from(expires));
        return new AuthResult(accessTokens.issue(user,session),refresh,expires);
    }
    private void revoke(UUID user, UUID session) {
        jdbc.update("UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,?) WHERE id=? AND user_id=?",now(),session,user);
        jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at,?) WHERE family_id=? AND user_id=?",now(),session,user);
    }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    public record AuthResult(String accessToken,String refreshToken,Instant sessionExpiresAt) {}
    private record RefreshState(UUID id,UUID session,boolean consumed,boolean revoked,boolean sessionRevoked,Instant expires,Instant sessionExpires) {}
}
