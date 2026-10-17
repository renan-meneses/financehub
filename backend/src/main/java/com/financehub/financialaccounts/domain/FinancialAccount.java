package com.financehub.financialaccounts.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="financial_accounts")
public class FinancialAccount {
    @Id private UUID id;
    @Column(name="workspace_id",nullable=false,updatable=false) private UUID workspaceId;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false,length=120) private String institution;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24,updatable=false) private AccountType type;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,columnDefinition="char(3)",updatable=false) private String currency;
    @Column(name="fraction_digits",nullable=false,updatable=false) private short fractionDigits;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private AccountStatus status;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Version private long version;
    protected FinancialAccount() {}
    public FinancialAccount(UUID workspaceId,String name,String institution,AccountType type,Money opening,UUID actor,Instant now) {
        this.id=UUID.randomUUID(); this.workspaceId=workspaceId; this.name=name.strip(); this.institution=institution.strip();
        this.type=type; this.currency=opening.currency().getCurrencyCode();
        this.fractionDigits=(short)opening.currency().getDefaultFractionDigits(); this.status=AccountStatus.ACTIVE;
        this.createdBy=actor; this.createdAt=now;
    }
    public void update(String name,String institution,AccountStatus status) {
        if(name!=null) this.name=name.strip();
        if(institution!=null) this.institution=institution.strip();
        if(status!=null) this.status=status;
    }
    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getCurrency() { return currency; }
    public short getFractionDigits() { return fractionDigits; }
    public long getVersion() { return version; }
}
