package com.financehub.financialaccounts.application;

import com.financehub.financialaccounts.domain.*;
import com.financehub.financialaccounts.infrastructure.*;
import com.financehub.organizations.application.WorkspaceAccess;
import com.financehub.organizations.infrastructure.WorkspaceRepository;
import com.financehub.identity.infrastructure.IdentityStore;
import com.financehub.shared.exceptions.ApiFailure;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final FinancialAccountRepository accounts;
    private final AccountPersistence ledger;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccess access;
    private final IdentityStore audit;
    private final Clock clock;
    public AccountService(FinancialAccountRepository accounts,AccountPersistence ledger,WorkspaceRepository workspaces,
                          WorkspaceAccess access,IdentityStore audit,Clock clock) {
        this.accounts=accounts; this.ledger=ledger; this.workspaces=workspaces; this.access=access; this.audit=audit; this.clock=clock;
    }
    @Transactional
    public Created create(UUID actor,UUID workspace,UUID key,String name,String institution,AccountType type,
                          String currency,String openingBalance,LocalDate openingDate) {
        var context=lockForWrite(actor,workspace);
        var money=new Money(new BigDecimal(openingBalance),Currency.getInstance(currency));
        if(openingDate.isAfter(LocalDate.now(clock.withZone(ZoneId.of(context.getTimezone())))))
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"future_opening","Opening date cannot be in the future");
        String fingerprint=fingerprint(name.strip(),institution.strip(),type.name(),currency,money.amount().toPlainString(),openingDate.toString());
        var previous=ledger.receipt(workspace,actor,key);
        if(previous.isPresent()) {
            if(!previous.get().fingerprint().equals(fingerprint))
                throw new ApiFailure(HttpStatus.CONFLICT,"idempotency_conflict","Key already used with different account data");
            return new Created(previous.get().response(),true);
        }
        var now=clock.instant();
        var account=accounts.saveAndFlush(new FinancialAccount(workspace,name,institution,type,money,actor,now));
        ledger.append(AccountLedgerEntry.opening(account,money,openingDate,actor,now));
        audit.audit(actor,workspace,"ACCOUNT_OPENED","ACCOUNT",account.getId());
        var result=summary(workspace,account.getId());
        ledger.remember(workspace,actor,key,fingerprint,result,now);
        return new Created(result,false);
    }
    @Transactional(readOnly=true)
    public Page<AccountView> list(UUID actor,UUID workspace,AccountType type,AccountStatus status,int page,int size) {
        access.requireMember(actor,workspace);
        var result=accounts.summaries(workspace,type,status,PageRequest.of(page,size));
        return new Page<>(result.getContent().stream().map(AccountSummary::view).toList(),page,size,result.getTotalElements());
    }
    @Transactional(readOnly=true)
    public AccountView get(UUID actor,UUID workspace,UUID id) { access.requireMember(actor,workspace); return summary(workspace,id); }
    @Transactional
    public AccountView update(UUID actor,UUID workspace,UUID id,String name,String institution,AccountStatus status,long version) {
        lockForWrite(actor,workspace);
        var account=accounts.findByWorkspaceIdAndId(workspace,id).orElseThrow(ApiFailure::notFound);
        if(account.getVersion()!=version) throw new ApiFailure(HttpStatus.CONFLICT,"stale_version","Account changed; reload before editing");
        account.update(name,institution,status);
        accounts.flush();
        audit.audit(actor,workspace,"ACCOUNT_UPDATED","ACCOUNT",id);
        return summary(workspace,id);
    }
    @Transactional(readOnly=true)
    public Page<AccountPersistence.LedgerView> history(UUID actor,UUID workspace,UUID account,int page,int size) {
        access.requireMember(actor,workspace);
        accounts.findByWorkspaceIdAndId(workspace,account).orElseThrow(ApiFailure::notFound);
        return new Page<>(ledger.history(workspace,account,page,size),page,size,ledger.historyCount(workspace,account));
    }
    private com.financehub.organizations.domain.Workspace lockForWrite(UUID actor,UUID workspace) {
        requireWrite(actor,workspace);
        var result=workspaces.lockActiveById(workspace).orElseThrow(ApiFailure::notFound);
        requireWrite(actor,workspace);
        return result;
    }
    private void requireWrite(UUID actor,UUID workspace) {
        if(!access.requireMember(actor,workspace).canWriteFinancials())
            throw new ApiFailure(HttpStatus.FORBIDDEN,"financial_write_forbidden","Role cannot change financial accounts");
    }
    private AccountView summary(UUID workspace,UUID id) { return accounts.summary(workspace,id).orElseThrow(ApiFailure::notFound).view(); }
    // Length prefixes prevent ambiguity between fields; canonical amounts make 1 and 1.00 equivalent.
    private String fingerprint(String... fields) {
        var canonical=new StringBuilder();
        for(String field:fields) canonical.append(field.length()).append(':').append(field);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record Created(AccountView account,boolean replayed) {}
    public record Page<T>(List<T> items,int page,int size,long total) {}
}
