package com.financehub.financialaccounts.infrastructure;

import com.financehub.financialaccounts.application.AccountView;
import com.financehub.financialaccounts.domain.AccountLedgerEntry;
import com.financehub.financialaccounts.domain.Money;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountPersistence {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AccountPersistence(EntityManager entities,JdbcTemplate jdbc,ObjectMapper json) {
        this.entities=entities; this.jdbc=jdbc; this.json=json;
    }
    public void append(AccountLedgerEntry entry) { entities.persist(entry); entities.flush(); }
    public Optional<Receipt> receipt(UUID workspace,UUID actor,UUID key) {
        return jdbc.query("SELECT fingerprint,response::text FROM account_creation_requests WHERE workspace_id=? AND actor_id=? AND request_key=?",
            (rs,n)->new Receipt(rs.getString(1),decode(rs.getString(2))),workspace,actor,key).stream().findFirst();
    }
    public void remember(UUID workspace,UUID actor,UUID key,String fingerprint,AccountView view,Instant now) {
        try {
            jdbc.update("""
                INSERT INTO account_creation_requests(workspace_id,actor_id,request_key,fingerprint,account_id,response,created_at)
                VALUES (?,?,?,?,?,?::jsonb,?)
                """,workspace,actor,key,fingerprint,view.id(),json.writeValueAsString(view),Timestamp.from(now));
        } catch(JsonProcessingException failure) { throw new IllegalStateException("Cannot serialize account receipt",failure); }
    }
    public List<LedgerView> history(UUID workspace,UUID account,int page,int size) {
        return jdbc.query("""
            SELECT id,kind,amount,currency,booking_date,created_at,
                   SUM(amount) OVER (ORDER BY booking_date,created_at,id ROWS UNBOUNDED PRECEDING) AS balance
            FROM account_ledger_entries WHERE workspace_id=? AND account_id=?
            ORDER BY booking_date,created_at,id LIMIT ? OFFSET ?
            """,(rs,n)->new LedgerView(rs.getObject("id",UUID.class),rs.getString("kind"),
                new Money(rs.getBigDecimal("amount"),Currency.getInstance(rs.getString("currency"))).amount().toPlainString(),
                rs.getString("currency"),rs.getObject("booking_date",LocalDate.class),rs.getTimestamp("created_at").toInstant(),
                new Money(rs.getBigDecimal("balance"),Currency.getInstance(rs.getString("currency"))).amount().toPlainString()),
            workspace,account,size,Math.multiplyExact(page,size));
    }
    public long historyCount(UUID workspace,UUID account) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger_entries WHERE workspace_id=? AND account_id=?",Long.class,workspace,account));
    }
    private AccountView decode(String value) {
        try { return json.readValue(value,AccountView.class); }
        catch(JsonProcessingException failure) { throw new IllegalStateException("Cannot read account receipt",failure); }
    }
    public record Receipt(String fingerprint,AccountView response) {}
    public record LedgerView(UUID id,String kind,String amount,String currency,LocalDate bookingDate,Instant createdAt,String balanceAfter) {}
}
