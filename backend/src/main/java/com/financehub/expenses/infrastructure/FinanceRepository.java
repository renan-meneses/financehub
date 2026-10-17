package com.financehub.expenses.infrastructure;

import com.financehub.expenses.application.FinanceViews.*;
import com.financehub.financialaccounts.domain.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceRepository {
    private final JdbcTemplate jdbc;
    public FinanceRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final RowMapper<Category> CATEGORY=(r,n)->new Category(r.getObject("id",UUID.class),r.getString("name"),AccountStatus.valueOf(r.getString("status")),r.getLong("version"));
    private static final String DEBT_SELECT="""
        SELECT d.*,a.name account_name,c.name category_name FROM debts d
        JOIN financial_accounts a ON a.workspace_id=d.workspace_id AND a.id=d.account_id
        JOIN expense_categories c ON c.workspace_id=d.workspace_id AND c.id=d.category_id
        """;
    private static final RowMapper<Debt> DEBT=(r,n)->new Debt(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getString("account_name"),
        r.getObject("category_id",UUID.class),r.getString("category_name"),r.getString("description"),money(r.getBigDecimal("amount"),r.getString("currency")),
        r.getString("currency"),r.getObject("due_date",LocalDate.class),DebtStatus.valueOf(r.getString("status")),r.getObject("paid_date",LocalDate.class));
    public static String money(BigDecimal amount,String currency) { return new Money(amount,Currency.getInstance(currency)).amount().toPlainString(); }
    public Optional<Category> category(UUID workspace,UUID id) { return jdbc.query("SELECT * FROM expense_categories WHERE workspace_id=? AND id=?",CATEGORY,workspace,id).stream().findFirst(); }
    public boolean nameExists(UUID workspace,UUID except,String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM expense_categories WHERE workspace_id=? AND id<>? AND lower(name)=lower(?))",Boolean.class,workspace,except,name));
    }
    public void createCategory(UUID workspace,UUID id,String name) { jdbc.update("INSERT INTO expense_categories(id,workspace_id,name,status) VALUES (?,?,?,'ACTIVE')",id,workspace,name); }
    public void updateCategory(UUID workspace,UUID id,String name,AccountStatus status) { jdbc.update("UPDATE expense_categories SET name=?,status=?,version=version+1 WHERE workspace_id=? AND id=?",name,status.name(),workspace,id); }
    public List<Category> categories(UUID workspace,int page,int size) { return jdbc.query("SELECT * FROM expense_categories WHERE workspace_id=? ORDER BY lower(name),id LIMIT ? OFFSET ?",CATEGORY,workspace,size,page*size); }
    public long categoryCount(UUID workspace) { return jdbc.queryForObject("SELECT count(*) FROM expense_categories WHERE workspace_id=?",Long.class,workspace); }
    public Optional<Debt> debt(UUID workspace,UUID id) { return jdbc.query(DEBT_SELECT+" WHERE d.workspace_id=? AND d.id=?",DEBT,workspace,id).stream().findFirst(); }
    public List<Debt> debts(UUID workspace,DebtStatus status,int page,int size) {
        return jdbc.query(DEBT_SELECT+" WHERE d.workspace_id=? AND (?::text IS NULL OR d.status=?) ORDER BY d.due_date,d.id LIMIT ? OFFSET ?",DEBT,workspace,status==null?null:status.name(),status==null?null:status.name(),size,page*size);
    }
    public long debtCount(UUID workspace,DebtStatus status) { return jdbc.queryForObject("SELECT count(*) FROM debts WHERE workspace_id=? AND (?::text IS NULL OR status=?)",Long.class,workspace,status==null?null:status.name(),status==null?null:status.name()); }
    public void createDebt(UUID workspace,UUID id,UUID account,UUID category,String description,Money money,LocalDate due,Instant now) {
        jdbc.update("""
            INSERT INTO debts(id,workspace_id,account_id,category_id,description,amount,currency,fraction_digits,due_date,status,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?)
            """,id,workspace,account,category,description,money.amount(),money.currency().getCurrencyCode(),money.currency().getDefaultFractionDigits(),due,Timestamp.from(now));
    }
    public void cancel(UUID workspace,UUID id) { jdbc.update("UPDATE debts SET status='CANCELLED' WHERE workspace_id=? AND id=?",workspace,id); }
    public void pay(UUID workspace,UUID id,LocalDate paidDate,UUID actor,Instant now) {
        jdbc.update("""
            INSERT INTO account_ledger_entries(id,workspace_id,account_id,currency,fraction_digits,amount,kind,booking_date,created_by,created_at,debt_id)
            SELECT ?,workspace_id,account_id,currency,fraction_digits,-amount,'DEBT_PAYMENT',?,?,?,id FROM debts WHERE workspace_id=? AND id=?
            """,UUID.randomUUID(),paidDate,actor,Timestamp.from(now),workspace,id);
        jdbc.update("UPDATE debts SET status='PAID',paid_date=? WHERE workspace_id=? AND id=?",paidDate,workspace,id);
    }
    public LocalDate openingDate(UUID workspace,UUID account) { return jdbc.queryForObject("SELECT booking_date FROM account_ledger_entries WHERE workspace_id=? AND account_id=? AND kind='OPENING_BALANCE'",LocalDate.class,workspace,account); }
    public Optional<Receipt> receipt(UUID workspace,UUID actor,String operation,UUID key) {
        return jdbc.query("SELECT fingerprint,response::text FROM finance_requests WHERE workspace_id=? AND actor_id=? AND operation=? AND request_key=?",
            (r,n)->new Receipt(r.getString(1),r.getString(2)),workspace,actor,operation,key).stream().findFirst();
    }
    public void remember(UUID workspace,UUID actor,String operation,UUID key,String fingerprint,String response) {
        jdbc.update("INSERT INTO finance_requests(workspace_id,actor_id,operation,request_key,fingerprint,response) VALUES (?,?,?,?,?,?::jsonb)",workspace,actor,operation,key,fingerprint,response);
    }
    public record Receipt(String fingerprint,String response) {}
    public record RawTotals(String currency,BigDecimal balance,BigDecimal pending,BigDecimal overdue,BigDecimal paid,BigDecimal previousPaid) {}
    public List<RawTotals> totals(UUID workspace,LocalDate today,LocalDate from,LocalDate to,LocalDate previousFrom) {
        return jdbc.query("""
            WITH balances AS (SELECT currency,sum(amount) balance FROM account_ledger_entries WHERE workspace_id=? GROUP BY currency),
            expenses AS (SELECT currency,
                coalesce(sum(amount) FILTER(WHERE status='PENDING'),0) pending,
                coalesce(sum(amount) FILTER(WHERE status='PENDING' AND due_date<?),0) overdue,
                coalesce(sum(amount) FILTER(WHERE status='PAID' AND paid_date BETWEEN ? AND ?),0) paid,
                coalesce(sum(amount) FILTER(WHERE status='PAID' AND paid_date>=? AND paid_date<?),0) previous_paid
                FROM debts WHERE workspace_id=? GROUP BY currency)
            SELECT b.currency,b.balance,coalesce(e.pending,0),coalesce(e.overdue,0),coalesce(e.paid,0),coalesce(e.previous_paid,0)
            FROM balances b LEFT JOIN expenses e ON b.currency=e.currency ORDER BY b.currency
            """,(r,n)->new RawTotals(r.getString(1),r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getBigDecimal(5),r.getBigDecimal(6)),workspace,today,from,to,previousFrom,from,workspace);
    }
    public List<CategoryTotal> categoryTotals(UUID workspace,LocalDate from,LocalDate to) {
        return jdbc.query("""
            SELECT c.id,c.name,d.currency,sum(d.amount) amount,
                round(sum(d.amount)*100/sum(sum(d.amount)) OVER(PARTITION BY d.currency),2) share
            FROM debts d JOIN expense_categories c ON c.workspace_id=d.workspace_id AND c.id=d.category_id
            WHERE d.workspace_id=? AND d.status='PAID' AND d.paid_date BETWEEN ? AND ?
            GROUP BY c.id,c.name,d.currency ORDER BY d.currency,amount DESC,c.id
            """,(r,n)->new CategoryTotal(r.getObject(1,UUID.class),r.getString(2),r.getString(3),money(r.getBigDecimal(4),r.getString(3)),r.getBigDecimal(5).toPlainString()),workspace,from,to);
    }
    public List<MonthTotal> months(UUID workspace,LocalDate from,LocalDate to) {
        return jdbc.query("""
            SELECT to_char(paid_date,'YYYY-MM') AS payment_month,currency,sum(amount) amount FROM debts
            WHERE workspace_id=? AND status='PAID' AND paid_date BETWEEN ? AND ?
            GROUP BY payment_month,currency ORDER BY payment_month,currency
            """,(r,n)->new MonthTotal(r.getString(1),r.getString(2),money(r.getBigDecimal(3),r.getString(2))),workspace,from,to);
    }
}
