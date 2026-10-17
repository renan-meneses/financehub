package com.financehub.expenses.presentation;

import com.financehub.expenses.application.*;
import com.financehub.expenses.application.FinanceViews.*;
import com.financehub.financialaccounts.application.AccountService;
import com.financehub.financialaccounts.domain.AccountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) { this.service=service; }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private <T> ResponseEntity<T> created(Created<T> result) { return ResponseEntity.status(result.replayed()?200:201).header("Idempotency-Replayed",Boolean.toString(result.replayed())).body(result.value()); }
    @GetMapping("/categories")
    public AccountService.Page<Category> categories(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.categories(actor(jwt),workspace,page,size);
    }
    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody CategoryCreate request) {
        return created(service.createCategory(actor(jwt),workspace,key,request.name()));
    }
    @PatchMapping("/categories/{id}")
    public Category updateCategory(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,@PathVariable UUID id,@Valid @RequestBody CategoryUpdate request) {
        return service.updateCategory(actor(jwt),workspace,id,request.name(),request.status(),request.version());
    }
    @GetMapping("/debts")
    public AccountService.Page<Debt> debts(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestParam(required=false) DebtStatus status,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.debts(actor(jwt),workspace,status,page,size);
    }
    @PostMapping("/debts")
    public ResponseEntity<Debt> createDebt(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody DebtCreate request) {
        return created(service.createDebt(actor(jwt),workspace,key,request.accountId(),request.categoryId(),request.description(),request.amount(),request.dueDate()));
    }
    @PostMapping("/debts/{id}/payments")
    public ResponseEntity<Debt> pay(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Payment request) {
        var result=service.pay(actor(jwt),workspace,id,key,request.paidDate());
        return ResponseEntity.ok().header("Idempotency-Replayed",Boolean.toString(result.replayed())).body(result.value());
    }
    @PostMapping("/debts/{id}/cancellation")
    public Debt cancel(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,@PathVariable UUID id) { return service.cancel(actor(jwt),workspace,id); }
    @GetMapping("/dashboard")
    public Analysis analysis(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Workspace-Id") UUID workspace,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        return service.analysis(actor(jwt),workspace,from,to);
    }
    public record CategoryCreate(@NotBlank @Size(max=120) String name) {}
    public record CategoryUpdate(@Size(max=120) @Pattern(regexp="(?s).*\\S.*") String name,AccountStatus status,@NotNull @Min(0) Long version) {}
    public record DebtCreate(@NotNull UUID accountId,@NotNull UUID categoryId,@NotBlank @Size(max=240) String description,
        @NotBlank @Pattern(regexp="[0-9]{1,19}(\\.[0-9]{1,4})?") String amount,@NotNull LocalDate dueDate) {}
    public record Payment(@NotNull LocalDate paidDate) {}
}
