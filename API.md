# API contracts

These routes now exist in source. Backend compilation and integration execution are blocked by unavailable Maven/Docker access. Local OpenAPI is enabled by Compose at `/v3/api-docs`; it is denied outside the `local` profile. Swagger UI is subject to the backend's restrictive CSP; use the JSON specification for now.

## Authentication

1. `GET /api/v1/auth/csrf` returns 204 and an `XSRF-TOKEN` cookie.
2. Send that cookie and its value as `X-XSRF-TOKEN` on every authentication POST. AngularJS `$http` does this for same-origin calls.
3. Login/refresh return `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 300 }` and set the HttpOnly `FH_REFRESH` cookie. Keep access tokens in memory.
4. Protected endpoints require `Authorization: Bearer <accessToken>`. Refresh cookies alone cannot authenticate these endpoints.

| Method/path under `/api/v1` | Request | Response |
| --- | --- | --- |
| `GET /auth/csrf` | None | 204 and CSRF cookie |
| `POST /auth/register` | email, password, displayName | 202 for new or existing address; new users receive verification mail |
| `POST /auth/verify-email` | token | 204; invalid/expired/consumed challenge: 401 |
| `POST /auth/resend-verification` | email | Generic 202 |
| `POST /auth/login` | email, password, deviceName | Access token + refresh cookie; invalid/unverified/suspended: 401 |
| `POST /auth/refresh` | Refresh cookie | Rotated cookie + access token; invalid/replayed: 401 and cleared cookie |
| `POST /auth/logout` | Refresh cookie | 204, revokes its session and clears cookie; repeated logout remains 204 |
| `POST /auth/forgot-password` | email | Generic 202; eligible account receives reset mail |
| `POST /auth/reset-password` | token, password | 204; revokes all user sessions |
| `GET /users/me` | Bearer token | id, email, displayName, version |
| `PATCH /users/me` | displayName, version | Updated profile; stale version: 409 |
| `POST /users/me/password` | currentPassword, newPassword | 204; revokes all sessions, including current |
| `GET /users/me/sessions` | page=0, size=20 | Bounded array of session summaries; no credentials |
| `DELETE /users/me/sessions/{id}` | Bearer token | 204; another user's session: 404 |
| `GET /workspaces` | page=0, size=20 | `{items,page,size,total}` for current memberships |
| `POST /workspaces` | name, kind, currency, timezone | 201 + Location; creates administrator membership atomically |
| `GET /workspaces/{id}` | Bearer token | Scoped workspace DTO; inaccessible/archived/revoked membership: 404 |

Passwords require 12 Unicode code points and at most 72 UTF-8 bytes to avoid BCrypt truncation. UI and DTO validation also cap input length. New workspace kind is PERSONAL or BUSINESS, currency an uppercase ISO code with 0–4 fraction digits, timezone a valid Java ZoneId. Maximum page size is 100; maximum page number is 10,000. An account can create at most 100 workspaces.

All authentication routes are rate limited except CSRF bootstrap. Login uses 10 attempts per normalized email per minute and 30 per transport address; other limits are in `AuthController`. A 429 response includes Retry-After: 60. Failed attempts count too.

Errors use RFC 9457 Problem Details. Malformed/invalid DTOs return 400; semantic policy failures return 422. Unknown JSON fields are rejected. Request/response bodies must never be logged on identity endpoints. Registration returns generic 202 rather than exposing whether an address exists. Password reset and verification are single-use actions, not replayable idempotent successes.

## Pending financial routes

Accounts, transactions, transfers, cards, invoices, dashboards, planning, reports and exports are not implemented. Their future tenant contract uses `X-Workspace-Id` validated against current membership, resource queries scoped to that workspace and PostgreSQL-backed idempotency for financial mutations. Workspace creation is a normal non-idempotent POST: retries may create another workspace; the client never automatically retries it.
