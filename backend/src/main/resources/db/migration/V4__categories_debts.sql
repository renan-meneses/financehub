CREATE TABLE expense_categories (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, id)
);
CREATE UNIQUE INDEX category_unique_name ON expense_categories(workspace_id, lower(name));
CREATE TABLE debts (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    account_id UUID NOT NULL,
    category_id UUID NOT NULL,
    description VARCHAR(240) NOT NULL CHECK (length(trim(description)) > 0),
    amount NUMERIC(23,4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    fraction_digits SMALLINT NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PAID','CANCELLED')),
    paid_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, account_id, currency, fraction_digits)
        REFERENCES financial_accounts(workspace_id, id, currency, fraction_digits),
    FOREIGN KEY (workspace_id, category_id) REFERENCES expense_categories(workspace_id, id),
    CHECK (amount = trunc(amount, fraction_digits)),
    CHECK ((status = 'PAID') = (paid_date IS NOT NULL))
);
CREATE INDEX debts_due ON debts(workspace_id, status, due_date, id);
CREATE INDEX debts_paid ON debts(workspace_id, paid_date) WHERE status='PAID';
ALTER TABLE account_ledger_entries DROP CONSTRAINT account_ledger_entries_kind_check;
ALTER TABLE account_ledger_entries ADD CONSTRAINT account_ledger_entries_kind_check CHECK (kind IN ('OPENING_BALANCE','DEBT_PAYMENT'));
ALTER TABLE account_ledger_entries ADD COLUMN debt_id UUID;
ALTER TABLE account_ledger_entries ADD FOREIGN KEY (workspace_id, debt_id) REFERENCES debts(workspace_id, id);
ALTER TABLE account_ledger_entries ADD CHECK ((kind='DEBT_PAYMENT') = (debt_id IS NOT NULL));
ALTER TABLE account_ledger_entries ADD CHECK (kind <> 'DEBT_PAYMENT' OR amount < 0);
CREATE UNIQUE INDEX debt_single_payment ON account_ledger_entries(workspace_id, debt_id) WHERE debt_id IS NOT NULL;
CREATE TABLE finance_requests (
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    actor_id UUID NOT NULL REFERENCES users(id),
    operation VARCHAR(80) NOT NULL,
    request_key UUID NOT NULL,
    fingerprint TEXT NOT NULL,
    response JSONB NOT NULL,
    PRIMARY KEY (workspace_id, actor_id, operation, request_key)
);
