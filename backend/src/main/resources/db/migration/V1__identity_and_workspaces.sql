CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT normalized_email CHECK (email = lower(trim(email)))
);
CREATE UNIQUE INDEX users_email_unique ON users (email);

CREATE TABLE roles (
    code VARCHAR(32) PRIMARY KEY,
    workspace_role BOOLEAN NOT NULL
);
INSERT INTO roles (code, workspace_role) VALUES
('SUPER_ADMIN', false), ('ORGANIZATION_ADMIN', true), ('FINANCIAL_MANAGER', true),
('ACCOUNTANT', true), ('MEMBER', true), ('READ_ONLY', true);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('PERSONAL', 'BUSINESS')),
    currency CHAR(3) NOT NULL DEFAULT 'BRL',
    timezone VARCHAR(64) NOT NULL DEFAULT 'America/Fortaleza',
    registration_identifier VARCHAR(64),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role_code VARCHAR(32) NOT NULL REFERENCES roles(code),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    PRIMARY KEY (workspace_id, user_id),
    CHECK (role_code <> 'SUPER_ADMIN')
);
CREATE INDEX workspace_members_user ON workspace_members (user_id, workspace_id) WHERE revoked_at IS NULL;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user ON refresh_tokens (user_id, expires_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES workspaces(id),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID,
    correlation_id VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_workspace_time ON audit_logs (workspace_id, occurred_at DESC, id);
CREATE FUNCTION deny_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit history is immutable';
END;
$$;
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();
