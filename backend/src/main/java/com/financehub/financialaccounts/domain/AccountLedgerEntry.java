package com.financehub.financialaccounts.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="account_ledger_entries")
@Immutable
public class AccountLedgerEntry {
    @Id private UUID id;
    @Column(name="workspace_id",nullable=false) private UUID workspaceId;
    @Column(name="account_id",nullable=false) private UUID accountId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,columnDefinition="char(3)") private String currency;
    @Column(name="fraction_digits",nullable=false) private short fractionDigits;
    @Column(nullable=false,precision=23,scale=4) private BigDecimal amount;
    @Column(nullable=false,length=24) private String kind;
    @Column(name="booking_date",nullable=false) private LocalDate bookingDate;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected AccountLedgerEntry() {}
    public static AccountLedgerEntry opening(FinancialAccount account,Money opening,LocalDate date,UUID actor,Instant now) {
        if(!account.getCurrency().equals(opening.currency().getCurrencyCode())) throw new IllegalArgumentException("Currency mismatch");
        var entry=new AccountLedgerEntry();
        entry.id=UUID.randomUUID(); entry.workspaceId=account.getWorkspaceId(); entry.accountId=account.getId();
        entry.currency=account.getCurrency(); entry.fractionDigits=account.getFractionDigits(); entry.amount=opening.amount();
        entry.kind="OPENING_BALANCE"; entry.bookingDate=date; entry.createdBy=actor; entry.createdAt=now;
        return entry;
    }
}
