package com.financehub.identity.infrastructure;

import com.financehub.identity.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityStore {
    private final JdbcTemplate jdbc;
    public IdentityStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<UserAccount> byEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = ?", this::map, email).stream().findFirst();
    }
    public Optional<UserAccount> byId(UUID id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", this::map, id).stream().findFirst();
    }
    public Optional<UserAccount> lock(UUID id) {
        return jdbc.query("SELECT * FROM users WHERE id = ? FOR UPDATE", this::map, id).stream().findFirst();
    }
    public boolean create(UUID id, String email, String hash, String name) {
        return jdbc.update("""
            INSERT INTO users(id, email, password_hash, display_name) VALUES (?, ?, ?, ?)
            ON CONFLICT (email) DO NOTHING
            """, id, email, hash, name) == 1;
    }
    public boolean sessionActive(UUID user, UUID session, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM auth_sessions s JOIN users u ON u.id=s.user_id
            WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>?
            AND u.suspended_at IS NULL AND u.email_verified_at IS NOT NULL)
            """, Boolean.class, session, user, java.sql.Timestamp.from(now)));
    }
    public void audit(UUID user, UUID workspace, String action, String type, UUID resource) {
        jdbc.update("INSERT INTO audit_logs(id, actor_id, workspace_id, action, resource_type, resource_id) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), user, workspace, action, type, resource);
    }
    private UserAccount map(ResultSet rs, int row) throws SQLException {
        return new UserAccount(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
            rs.getString("display_name"), instant(rs, "email_verified_at"), instant(rs, "suspended_at"), rs.getLong("version"));
    }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
