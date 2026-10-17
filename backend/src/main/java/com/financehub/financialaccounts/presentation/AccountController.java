package com.financehub.financialaccounts.presentation;

import com.financehub.financialaccounts.application.*;
import com.financehub.financialaccounts.domain.*;
import com.financehub.financialaccounts.infrastructure.AccountPersistence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<AccountView> create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Create request) {
        var created=service.create(UUID.fromString(jwt.getSubject()),workspace,key,request.name(),request.institution(),request.type(),
            request.currency(),request.openingBalance(),request.openingDate());
        return ResponseEntity.status(created.replayed()?200:201).location(URI.create("/api/v1/accounts/"+created.account().id()))
            .header("Idempotency-Replayed",Boolean.toString(created.replayed())).body(created.account());
    }
    @GetMapping
    public AccountService.Page<AccountView> list(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestParam(required=false) AccountType type,@RequestParam(required=false) AccountStatus status,
            @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(UUID.fromString(jwt.getSubject()),workspace,type,status,page,size);
    }
    @GetMapping("/{id}")
    public AccountView get(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,@PathVariable UUID id) {
        return service.get(UUID.fromString(jwt.getSubject()),workspace,id);
    }
    @PatchMapping("/{id}")
    public AccountView update(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,@PathVariable UUID id,
            @Valid @RequestBody Update request) {
        return service.update(UUID.fromString(jwt.getSubject()),workspace,id,request.name(),request.institution(),request.status(),request.version());
    }
    @GetMapping("/{id}/ledger")
    public AccountService.Page<AccountPersistence.LedgerView> history(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Workspace-Id") UUID workspace,@PathVariable UUID id,
            @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.history(UUID.fromString(jwt.getSubject()),workspace,id,page,size);
    }
    public record Create(@NotBlank @Size(max=120) String name,@NotNull @Size(max=120) String institution,
            @NotNull AccountType type,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp="-?[0-9]{1,19}(\\.[0-9]{1,4})?") String openingBalance,@NotNull LocalDate openingDate) {}
    public record Update(@Size(max=120) @Pattern(regexp="(?s).*\\S.*") String name,@Size(max=120) String institution,
                         AccountStatus status,@NotNull @Min(0) Long version) {}
}
