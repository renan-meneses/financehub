package com.financehub.organizations.application;

import com.financehub.organizations.domain.WorkspaceRole;
import com.financehub.shared.exceptions.ApiFailure;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceAccess {
    private final JdbcTemplate jdbc;
    public WorkspaceAccess(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public WorkspaceRole requireMember(UUID user,UUID workspace) {
        var roles=jdbc.query("""
            SELECT m.role_code FROM workspace_members m JOIN workspaces w ON w.id=m.workspace_id
            JOIN users u ON u.id=m.user_id WHERE m.user_id=? AND m.workspace_id=? AND m.revoked_at IS NULL
            AND w.archived_at IS NULL AND u.suspended_at IS NULL AND u.email_verified_at IS NOT NULL
            """,(rs,n)->WorkspaceRole.valueOf(rs.getString(1)),user,workspace);
        if(roles.isEmpty()) throw ApiFailure.notFound();
        return roles.getFirst();
    }
}
