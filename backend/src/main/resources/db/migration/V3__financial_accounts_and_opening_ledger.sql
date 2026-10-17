CREATE TABLE financial_accounts (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    institution VARCHAR(120) NOT NULL,
    type VARCHAR(24) NOT NULL CHECK (type IN ('CHECKING','SAVINGS','CASH','DIGITAL','INVESTMENT','BUSINESS','OTHER')),
    currency CHAR(3) NOT NULL,
    fraction_digits SMALLINT NOT NULL CHECK (fraction_digits BETWEEN 0 AND 4),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, id),
    UNIQUE (workspace_id, id, currency, fraction_digits)
);
CREATE INDEX financial_accounts_list ON financial_accounts(workspace_id, status, type, created_at, id);
CREATE TABLE account_ledger_entries (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    account_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    fraction_digits SMALLINT NOT NULL,
    amount NUMERIC(23,4) NOT NULL,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('OPENING_BALANCE')),
    booking_date DATE NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (workspace_id, account_id, currency, fraction_digits)
        REFERENCES financial_accounts(workspace_id, id, currency, fraction_digits),
    CHECK (amount = trunc(amount, fraction_digits))
);
CREATE UNIQUE INDEX account_single_opening ON account_ledger_entries(workspace_id, account_id) WHERE kind='OPENING_BALANCE';
CREATE INDEX account_ledger_history ON account_ledger_entries(workspace_id, account_id, booking_date, created_at, id);
CREATE FUNCTION deny_ledger_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Account ledger is immutable';
END;
$$;
CREATE TRIGGER account_ledger_immutable BEFORE UPDATE OR DELETE ON account_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION deny_ledger_mutation();
CREATE TRIGGER account_ledger_no_truncate BEFORE TRUNCATE ON account_ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION deny_ledger_mutation();
CREATE TABLE account_creation_requests (
    workspace_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id),
    request_key UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    account_id UUID NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, actor_id, request_key),
    FOREIGN KEY (workspace_id, account_id) REFERENCES financial_accounts(workspace_id, id)
);
