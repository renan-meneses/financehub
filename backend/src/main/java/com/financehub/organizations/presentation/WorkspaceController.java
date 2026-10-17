package com.financehub.organizations.presentation;

import com.financehub.organizations.application.WorkspaceService;
import com.financehub.organizations.domain.Workspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
@Validated
public class WorkspaceController {
    private final WorkspaceService workspaces;
    public WorkspaceController(WorkspaceService workspaces) { this.workspaces=workspaces; }
    @GetMapping WorkspaceService.Page list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
                                          @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return workspaces.list(UUID.fromString(jwt.getSubject()),page,size);
    }
    @GetMapping("/{id}") WorkspaceService.View get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
        return workspaces.get(UUID.fromString(jwt.getSubject()),id);
    }
    @PostMapping ResponseEntity<WorkspaceService.View> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Create request) {
        var result=workspaces.create(UUID.fromString(jwt.getSubject()),request.name(),request.kind(),request.currency(),request.timezone());
        return ResponseEntity.created(URI.create("/api/v1/workspaces/"+result.id())).body(result);
    }
    public record Create(@NotBlank @Size(max=120) String name,@NotNull Workspace.Kind kind,
                         @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotBlank @Size(max=64) String timezone) {}
}
