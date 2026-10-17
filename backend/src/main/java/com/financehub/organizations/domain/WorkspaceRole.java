package com.financehub.organizations.domain;

/** Permissions are explicit; enum ordering is never treated as authority. */
public enum WorkspaceRole {
    ORGANIZATION_ADMIN, FINANCIAL_MANAGER, ACCOUNTANT, MEMBER, READ_ONLY;
    public boolean canManageMembers() { return this==ORGANIZATION_ADMIN; }
    public boolean canWriteFinancials() { return this==ORGANIZATION_ADMIN || this==FINANCIAL_MANAGER || this==MEMBER; }
    public boolean canExport() { return this==ORGANIZATION_ADMIN || this==FINANCIAL_MANAGER || this==ACCOUNTANT; }
}
