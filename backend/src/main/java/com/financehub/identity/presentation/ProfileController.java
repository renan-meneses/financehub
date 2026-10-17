package com.financehub.identity.presentation;

import com.financehub.identity.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@Validated
public class ProfileController {
    private final ProfileService profiles;
    private final IdentityService identity;
    public ProfileController(ProfileService profiles,IdentityService identity) { this.profiles=profiles; this.identity=identity; }
    @GetMapping ProfileService.Profile me(@AuthenticationPrincipal Jwt jwt) { return profiles.get(UUID.fromString(jwt.getSubject())); }
    @PatchMapping ProfileService.Profile update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Update request) {
        return profiles.rename(UUID.fromString(jwt.getSubject()),request.displayName(),request.version());
    }
    @PostMapping("/password") ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Password request) {
        identity.changePassword(UUID.fromString(jwt.getSubject()),request.currentPassword(),request.newPassword());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/sessions") List<ProfileService.Session> sessions(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return profiles.sessions(UUID.fromString(jwt.getSubject()),page,size);
    }
    @DeleteMapping("/sessions/{id}") ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
        profiles.revoke(UUID.fromString(jwt.getSubject()),id);
        return ResponseEntity.noContent().build();
    }
    public record Update(@NotBlank @Size(max=120) String displayName,@Min(0) long version) {}
    public record Password(@NotBlank @Size(max=256) String currentPassword,@NotBlank @Size(min=12,max=72) String newPassword) {}
}
