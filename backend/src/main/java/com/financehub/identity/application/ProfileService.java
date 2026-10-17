package com.financehub.identity.application;

import com.financehub.identity.infrastructure.IdentityStore;
import com.financehub.shared.exceptions.ApiFailure;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final IdentityStore users;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ProfileService(IdentityStore users,JdbcTemplate jdbc,Clock clock) { this.users=users; this.jdbc=jdbc; this.clock=clock; }
    @Transactional(readOnly=true)
    public Profile get(UUID id) {
        var user=users.byId(id).orElseThrow(ApiFailure::unauthenticated);
        if(!user.active()) throw ApiFailure.unauthenticated();
        return new Profile(user.id(),user.email(),user.displayName(),user.version());
    }
    @Transactional
    public Profile rename(UUID id,String name,long version) {
        var user=users.lock(id).orElseThrow(ApiFailure::unauthenticated);
        if(!user.active()) throw ApiFailure.unauthenticated();
        if(user.version()!=version) throw new ApiFailure(HttpStatus.CONFLICT,"stale_version","Profile changed; reload before editing");
        jdbc.update("UPDATE users SET display_name=?,version=version+1 WHERE id=?",name.strip(),id);
        users.audit(id,null,"PROFILE_UPDATED","USER",id);
        return get(id);
    }
    @Transactional(readOnly=true)
    public List<Session> sessions(UUID user,int page,int size) {
        return jdbc.query("""
            SELECT id,device_name,created_at,expires_at,revoked_at FROM auth_sessions WHERE user_id=?
            ORDER BY created_at DESC,id LIMIT ? OFFSET ?
            """,(rs,n)->new Session(rs.getObject("id",UUID.class),rs.getString("device_name"),rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("revoked_at")!=null),user,size,Math.multiplyExact(page,size));
    }
    @Transactional
    public void revoke(UUID user,UUID session) {
        users.lock(user).orElseThrow(ApiFailure::unauthenticated);
        int changed=jdbc.update("UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,?) WHERE id=? AND user_id=?",Timestamp.from(clock.instant()),session,user);
        if(changed!=1) throw ApiFailure.notFound();
        jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at,?) WHERE family_id=? AND user_id=?",Timestamp.from(clock.instant()),session,user);
        users.audit(user,null,"SESSION_REVOKED","SESSION",session);
    }
    public record Profile(UUID id,String email,String displayName,long version) {}
    public record Session(UUID id,String deviceName,Instant createdAt,Instant expiresAt,boolean revoked) {}
}
