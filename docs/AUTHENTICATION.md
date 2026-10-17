# Identity implementation and operation

## Flow

```mermaid
sequenceDiagram
    participant Browser
    participant API
    participant DB as PostgreSQL
    participant Worker as SMTP worker
    Browser->>API: CSRF bootstrap, then register
    API->>DB: Insert user, hashed challenge, email outbox, audit
    API-->>Browser: 202
    Worker->>DB: Lock due outbox row (SKIP LOCKED)
    Worker-->>Browser: Verification email via SMTP
    Browser->>API: Verify challenge, then login
    API->>DB: Validate credentials, lock user, create session + hashed refresh
    API-->>Browser: Access JWT + HttpOnly refresh cookie
    Browser->>API: Refresh with CSRF header and cookie
    API->>DB: Lock user, recheck token, consume old token, insert successor
    API-->>Browser: New access JWT + rotated cookie
    Browser->>API: Replay consumed refresh token
    API->>DB: Revoke entire session family and append audit; commit
    API-->>Browser: 401 and clear cookie
```

Session mutations use a consistent user-first lock order. A fresh session has an absolute 30-day expiration; rotation never extends it. The issued JWT expires in five minutes, with Spring Security's timestamp validator clock-skew allowance. Password changes/reset revoke all sessions and outstanding password-reset challenges. Email verification does not automatically sign the browser in.

The application uses Spring's JPA transaction manager with the same datasource for JPA and JdbcTemplate operations. Identity operations use explicit SQL for locks and atomic mutations; Workspace is the first Hibernate entity. Database behavior and transaction participation still need execution through the integration suite.

## Key configuration

- `JWT_KEY_DIRECTORY`: mounted directory containing `<kid>.pub` X.509 PEM public keys.
- `JWT_KEY_ID`: active key ID, restricted to letters/digits/underscore/hyphen.
- `<active-kid>.key`: PKCS#8 PEM private key. Only the active private key is loaded.
- `JWT_ISSUER`: exact issuer URI.
- `PUBLIC_URL`: user-facing origin used in email links.

Keys must be RSA with at least 2048 bits. Local Compose generates a 3072-bit development pair in an isolated named volume. No keys are committed or embedded in images.

For production, generate and distribute keys with a secrets manager. To rotate: distribute the new public key to all replicas first, keep the old public key, then distribute the new private key and change the active ID across replicas. Restart/reload replicas through deployment; in-process hot reload is not implemented. Retain old public keys for at least the access-token lifetime plus clock skew after the last old signer stops. Never expose the key directory through NGINX.

## Email

SMTP settings: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, `MAIL_FROM`. STARTTLS is required by default. Local Compose uses Mailpit without TLS on its private network.

The scheduled worker claims one pending row using `FOR UPDATE SKIP LOCKED`, sends with bounded socket timeouts, and deletes successful messages. On failure it schedules exponential retry, capped at one hour. Expired messages are deleted. Multiple replicas may run workers; a message's row lock prevents simultaneous delivery, but crash recovery may produce duplicates. Worker metrics/alerts and operator retry UI are not implemented.

Email links use a URL fragment (`/#!/verify-email?token=...`) so the secret does not enter normal HTTP access logs. Frontend code submits the token in a POST body with CSRF protection.

## Local smoke sequence, not yet executed

1. Start Compose; visit port 8081 and register a unique email.
2. Open port 8025, follow the verification link and confirm.
3. Log in, create a workspace and refresh the browser. The client should restore access through refresh.
4. Open a second account in a separate browser profile. The first account's workspace ID must return 404 there.
5. Request password recovery and follow its email link. Old sessions must receive 401 afterward.
6. Verify logout clears the refresh cookie and revokes access. Inspect generated OpenAPI JSON locally.
