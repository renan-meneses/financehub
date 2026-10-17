package com.financehub.expenses.application;

import com.financehub.expenses.application.FinanceViews.*;
import com.financehub.expenses.infrastructure.FinanceRepository;
import com.financehub.financialaccounts.application.AccountService;
import com.financehub.financialaccounts.domain.*;
import com.financehub.identity.infrastructure.IdentityStore;
import com.financehub.organizations.application.WorkspaceAccess;
import com.financehub.organizations.infrastructure.WorkspaceRepository;
import com.financehub.shared.exceptions.ApiFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FinanceService {
    private final FinanceRepository store;
    private final WorkspaceAccess access;
    private final WorkspaceRepository workspaces;
    private final AccountService accounts;
    private final IdentityStore audit;
    private final ObjectMapper json;
    private final Clock clock;
    public FinanceService(FinanceRepository store,WorkspaceAccess access,WorkspaceRepository workspaces,AccountService accounts,IdentityStore audit,ObjectMapper json,Clock clock) {
        this.store=store; this.access=access; this.workspaces=workspaces; this.accounts=accounts; this.audit=audit; this.json=json; this.clock=clock;
    }
    private LocalDate lock(UUID actor,UUID workspace) {
        requireWrite(actor,workspace);
        var context=workspaces.lockActiveById(workspace).orElseThrow(ApiFailure::notFound);
        requireWrite(actor,workspace);
        return LocalDate.now(clock.withZone(ZoneId.of(context.getTimezone())));
    }
    private void requireWrite(UUID actor,UUID workspace) {
        if(!access.requireMember(actor,workspace).canWriteFinancials()) throw new ApiFailure(HttpStatus.FORBIDDEN,"financial_write_forbidden","Role cannot change finances");
    }
    private static ApiFailure conflict(String code) { return new ApiFailure(HttpStatus.CONFLICT,code,"Financial state conflicts with request"); }
    private static ApiFailure invalid(String code) { return new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,code,"Operation violates financial rules"); }
    private <T> Created<T> once(UUID actor,UUID workspace,String operation,UUID key,Object payload,Class<T> type,Supplier<T> action) {
        try {
            String fingerprint=json.writeValueAsString(payload);
            var receipt=store.receipt(workspace,actor,operation,key);
            if(receipt.isPresent()) {
                if(!receipt.get().fingerprint().equals(fingerprint)) throw conflict("idempotency_conflict");
                return new Created<>(json.readValue(receipt.get().response(),type),true);
            }
            T result=action.get();
            store.remember(workspace,actor,operation,key,fingerprint,json.writeValueAsString(result));
            return new Created<>(result,false);
        } catch(JsonProcessingException e) { throw new IllegalStateException("Cannot process finance receipt",e); }
    }
    @Transactional(readOnly=true)
    public AccountService.Page<Category> categories(UUID actor,UUID workspace,int page,int size) {
        access.requireMember(actor,workspace);
        return new AccountService.Page<>(store.categories(workspace,page,size),page,size,store.categoryCount(workspace));
    }
    @Transactional
    public Created<Category> createCategory(UUID actor,UUID workspace,UUID key,String name) {
        lock(actor,workspace);String clean=name.strip();
        return once(actor,workspace,"CATEGORY_CREATE",key,clean,Category.class,()->{
            var id=UUID.randomUUID();
            if(store.nameExists(workspace,id,clean)) throw conflict("category_name_exists");
            store.createCategory(workspace,id,clean);audit.audit(actor,workspace,"CATEGORY_CREATED","CATEGORY",id);
            return store.category(workspace,id).orElseThrow();
        });
    }
    @Transactional
    public Category updateCategory(UUID actor,UUID workspace,UUID id,String name,AccountStatus status,long version) {
        lock(actor,workspace);var current=store.category(workspace,id).orElseThrow(ApiFailure::notFound);
        if(current.version()!=version) throw conflict("stale_version");
        String clean=name==null?current.name():name.strip();
        if(store.nameExists(workspace,id,clean)) throw conflict("category_name_exists");
        store.updateCategory(workspace,id,clean,status==null?current.status():status);
        audit.audit(actor,workspace,"CATEGORY_UPDATED","CATEGORY",id);
        return store.category(workspace,id).orElseThrow();
    }
    @Transactional(readOnly=true)
    public AccountService.Page<Debt> debts(UUID actor,UUID workspace,DebtStatus status,int page,int size) {
        access.requireMember(actor,workspace);
        return new AccountService.Page<>(store.debts(workspace,status,page,size),page,size,store.debtCount(workspace,status));
    }
    @Transactional
    public Created<Debt> createDebt(UUID actor,UUID workspace,UUID key,UUID account,UUID category,String description,String amount,LocalDate dueDate) {
        lock(actor,workspace);
        var accountView=accounts.get(actor,workspace,account);
        var money=new Money(new BigDecimal(amount),Currency.getInstance(accountView.currency()));
        if(money.amount().signum()<=0) throw invalid("invalid_amount");
        return once(actor,workspace,"DEBT_CREATE",key,List.of(account,category,description.strip(),money.amount().toPlainString(),dueDate),Debt.class,()->{
            if(accountView.status()!=AccountStatus.ACTIVE) throw invalid("account_archived");
            if(store.category(workspace,category).orElseThrow(ApiFailure::notFound).status()!=AccountStatus.ACTIVE) throw invalid("category_archived");
            UUID id=UUID.randomUUID();
            store.createDebt(workspace,id,account,category,description.strip(),money,dueDate,clock.instant());
            audit.audit(actor,workspace,"DEBT_CREATED","DEBT",id);
            return store.debt(workspace,id).orElseThrow();
        });
    }
    @Transactional
    public Created<Debt> pay(UUID actor,UUID workspace,UUID id,UUID key,LocalDate paidDate) {
        var today=lock(actor,workspace);
        return once(actor,workspace,"DEBT_PAY:"+id,key,paidDate,Debt.class,()->{
            var debt=store.debt(workspace,id).orElseThrow(ApiFailure::notFound);
            if(debt.status()==DebtStatus.PAID && paidDate.equals(debt.paidDate())) return debt;
            if(debt.status()!=DebtStatus.PENDING) throw conflict("debt_not_pending");
            var account=accounts.get(actor,workspace,debt.accountId());
            if(account.status()!=AccountStatus.ACTIVE) throw invalid("account_archived");
            if(paidDate.isAfter(today) || paidDate.isBefore(store.openingDate(workspace,debt.accountId()))) throw invalid("invalid_payment_date");
            // Accounts currently have no overdraft policy: require funds at settlement.
            if(new BigDecimal(account.currentBalance()).compareTo(new BigDecimal(debt.amount()))<0) throw invalid("insufficient_funds");
            store.pay(workspace,id,paidDate,actor,clock.instant());
            audit.audit(actor,workspace,"DEBT_PAID","DEBT",id);
            return store.debt(workspace,id).orElseThrow();
        });
    }
    @Transactional
    public Debt cancel(UUID actor,UUID workspace,UUID id) {
        lock(actor,workspace);var debt=store.debt(workspace,id).orElseThrow(ApiFailure::notFound);
        if(debt.status()==DebtStatus.CANCELLED) return debt;
        if(debt.status()!=DebtStatus.PENDING) throw conflict("debt_not_pending");
        store.cancel(workspace,id);audit.audit(actor,workspace,"DEBT_CANCELLED","DEBT",id);
        return store.debt(workspace,id).orElseThrow();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Analysis analysis(UUID actor,UUID workspace,LocalDate from,LocalDate to) {
        access.requireMember(actor,workspace);
        var context=workspaces.findById(workspace).orElseThrow(ApiFailure::notFound);
        var today=LocalDate.now(clock.withZone(ZoneId.of(context.getTimezone())));
        long days=ChronoUnit.DAYS.between(from,to)+1;
        if(days<1 || days>366 || to.isAfter(today)) throw invalid("invalid_period");
        var totals=store.totals(workspace,today,from,to,from.minusDays(days)).stream().map(r->{
            var available=r.balance().subtract(r.pending());
            String change=r.previousPaid().signum()==0?null:r.paid().subtract(r.previousPaid()).multiply(BigDecimal.valueOf(100)).divide(r.previousPaid(),2,RoundingMode.HALF_UP).toPlainString();
            return new Totals(r.currency(),FinanceRepository.money(r.balance(),r.currency()),FinanceRepository.money(r.pending(),r.currency()),
                FinanceRepository.money(r.overdue(),r.currency()),FinanceRepository.money(r.paid(),r.currency()),FinanceRepository.money(r.previousPaid(),r.currency()),
                FinanceRepository.money(available,r.currency()),available.signum()<0,change);
        }).toList();
        var recorded=store.months(workspace,from,to);var months=new ArrayList<MonthTotal>();
        for(var month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1)) {
            String label=month.toString();
            for(var total:totals) months.add(recorded.stream().filter(m->m.month().equals(label)&&m.currency().equals(total.currency())).findFirst()
                .orElse(new MonthTotal(label,total.currency(),FinanceRepository.money(BigDecimal.ZERO,total.currency()))));
        }
        return new Analysis(from,to,today,totals,store.categoryTotals(workspace,from,to),months);
    }
}
