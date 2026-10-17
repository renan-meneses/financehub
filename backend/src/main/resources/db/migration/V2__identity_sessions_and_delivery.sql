CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    device_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    UNIQUE (id, user_id)
);
-- V1 had no issuance endpoint. Discard any development tokens without a session.
DELETE FROM refresh_tokens;
ALTER TABLE refresh_tokens ADD CONSTRAINT refresh_session_owner
    FOREIGN KEY (family_id, user_id) REFERENCES auth_sessions(id, user_id);
CREATE INDEX auth_sessions_user ON auth_sessions(user_id, created_at DESC);
CREATE TABLE identity_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX identity_challenges_user ON identity_challenges(user_id, purpose);
CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX email_outbox_pending ON email_outbox(next_attempt_at);
CREATE TABLE auth_rate_limits (
    key_hash CHAR(64) PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL
);
