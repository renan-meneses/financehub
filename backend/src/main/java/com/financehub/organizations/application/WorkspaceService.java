package com.financehub.organizations.application;

import com.financehub.identity.infrastructure.IdentityStore;
import com.financehub.organizations.domain.*;
import com.financehub.organizations.infrastructure.WorkspaceRepository;
import com.financehub.shared.exceptions.ApiFailure;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    private final WorkspaceRepository repository;
    private final WorkspaceAccess access;
    private final IdentityStore users;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public WorkspaceService(WorkspaceRepository repository,WorkspaceAccess access,IdentityStore users,JdbcTemplate jdbc,Clock clock) {
        this.repository=repository; this.access=access; this.users=users; this.jdbc=jdbc; this.clock=clock;
    }
    @Transactional
    public View create(UUID user,String name,Workspace.Kind kind,String currency,String timezone) {
        var account=users.lock(user).orElseThrow(ApiFailure::unauthenticated);
        if(!account.active()) throw ApiFailure.unauthenticated();
        var iso=Currency.getInstance(currency);
        if(iso.getDefaultFractionDigits()<0 || iso.getDefaultFractionDigits()>4) throw new IllegalArgumentException("Unsupported currency");
        ZoneId.of(timezone);
        int count=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM workspaces WHERE created_by=?",Integer.class,user));
        if(count>=100) throw new ApiFailure(HttpStatus.CONFLICT,"workspace_limit","Workspace creation limit reached");
        var workspace=repository.saveAndFlush(new Workspace(UUID.randomUUID(),name.strip(),kind,currency,timezone,user,clock.instant()));
        jdbc.update("INSERT INTO workspace_members(workspace_id,user_id,role_code) VALUES (?,?,'ORGANIZATION_ADMIN')",workspace.getId(),user);
        users.audit(user,workspace.getId(),"WORKSPACE_CREATED","WORKSPACE",workspace.getId());
        return view(workspace,WorkspaceRole.ORGANIZATION_ADMIN);
    }
    @Transactional(readOnly=true)
    public View get(UUID user,UUID workspace) {
        var role=access.requireMember(user,workspace);
        return view(repository.findById(workspace).orElseThrow(ApiFailure::notFound),role);
    }
    @Transactional(readOnly=true)
    public Page list(UUID user,int page,int size) {
        var items=jdbc.query("""
            SELECT w.id,w.name,w.kind,w.currency,w.timezone,w.version,m.role_code FROM workspaces w
            JOIN workspace_members m ON m.workspace_id=w.id
            WHERE m.user_id=? AND m.revoked_at IS NULL AND w.archived_at IS NULL
            ORDER BY w.created_at,w.id LIMIT ? OFFSET ?
            """,(rs,n)->new View(rs.getObject("id",UUID.class),rs.getString("name"),Workspace.Kind.valueOf(rs.getString("kind")),
                rs.getString("currency"),rs.getString("timezone"),WorkspaceRole.valueOf(rs.getString("role_code")),rs.getLong("version")),
            user,size,Math.multiplyExact(page,size));
        Long total=jdbc.queryForObject("""
            SELECT count(*) FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id
            WHERE m.user_id=? AND m.revoked_at IS NULL AND w.archived_at IS NULL
            """,Long.class,user);
        return new Page(items,page,size,total==null?0:total);
    }
    private View view(Workspace w,WorkspaceRole role) {
        return new View(w.getId(),w.getName(),w.getKind(),w.getCurrency(),w.getTimezone(),role,w.getVersion());
    }
    public record View(UUID id,String name,Workspace.Kind kind,String currency,String timezone,WorkspaceRole role,long version) {}
    public record Page(List<View> items,int page,int size,long total) {}
}
