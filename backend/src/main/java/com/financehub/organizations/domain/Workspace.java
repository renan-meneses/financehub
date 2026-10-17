package com.financehub.organizations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="workspaces")
public class Workspace {
    @Id private UUID id;
    @Column(nullable=false,length=120) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Kind kind;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(nullable=false,columnDefinition="char(3)") private String currency;
    @Column(nullable=false,length=64) private String timezone;
    @Column(name="registration_identifier",length=64) private String registrationIdentifier;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="archived_at") private Instant archivedAt;
    @Version private long version;
    protected Workspace() {}
    public Workspace(UUID id,String name,Kind kind,String currency,String timezone,UUID creator,Instant createdAt) {
        this.id=id; this.name=name; this.kind=kind; this.currency=currency; this.timezone=timezone;
        this.createdBy=creator; this.createdAt=createdAt;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Kind getKind() { return kind; }
    public String getCurrency() { return currency; }
    public String getTimezone() { return timezone; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
    public enum Kind { PERSONAL, BUSINESS }
}
