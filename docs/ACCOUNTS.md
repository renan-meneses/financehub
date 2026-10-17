# Financial accounts: first increment

This increment is implemented in source and connected to AngularJS. Its backend integration and browser scenarios have not executed in the agent environment. The user separately confirmed that registration and login work locally.

## Behavior

Create checking, savings, cash, digital-bank, investment, business or other accounts in a workspace. Each account has an immutable currency and type, editable name/institution, ACTIVE/ARCHIVED status and an optimistic version. Archiving preserves history; restoring ACTIVE is supported.

Creation always appends one OPENING_BALANCE entry, even for zero. Negative opening balances are allowed to represent an already overdrawn account. The opening date cannot be later than today in the workspace timezone. The opening entry is a starting balance, not recognized income or expense; future reports must exclude it from those categories.

There is no stored mutable current-balance field. Account balances are computed by a JPQL aggregate over signed ledger entries. Ledger history uses a PostgreSQL window sum before applying pagination, ordered by booking date, creation instant and UUID. With this increment, only opening entries can be created; income/expense, transfer, adjustment and reconciliation operations remain pending.

All monetary API fields are decimal strings. Currency precision comes from Java Currency, restricted to 0–4 digits. Extra nonzero fractional digits are rejected. Money accepts at most 19 digits of precision; NUMERIC(23,4) accommodates those values without rounding. Database checks additionally enforce account-specific minor-unit precision and composite foreign keys match workspace, account, currency and precision. Metadata requests cannot include currentBalance, openingBalance, currency or type.

## REST contracts

All routes require a bearer JWT and UUID `X-Workspace-Id`. Inaccessible workspaces/accounts return 404. READ_ONLY and ACCOUNTANT may read; ORGANIZATION_ADMIN, FINANCIAL_MANAGER and MEMBER may write. The initial permission policy is explicit in WorkspaceRole.

`POST /api/v1/accounts` requires UUID `Idempotency-Key`:

```json
{
  "name": "Everyday account",
  "institution": "Synthetic Bank",
  "type": "CHECKING",
  "currency": "BRL",
  "openingBalance": "1500.00",
  "openingDate": "2026-01-01"
}
```

The response is 201 with Location on first creation; identical retries return 200 and `Idempotency-Replayed: true`. It contains id, name, institution, type, currency, status, version, createdAt and currentBalance. A replay returns the original response snapshot, even if metadata was subsequently edited. Retrieve the account to obtain its current state.

The key is scoped to actor and workspace. Canonicalized names/institution and exact currency amounts are fingerprinted; `1500` and `1500.00` are equivalent for BRL. Reusing a key with different data returns 409. The account, opening entry, audit and response receipt commit together. A workspace row lock serializes creation/replay and metadata writes in this initial implementation. Future membership mutations must take the same workspace lock to coordinate permission changes. Account-level locking can replace this coarse lock when measured contention warrants it.

`GET /api/v1/accounts` supports optional `type` and `status`, plus `page=0&size=20`. Maximum size is 100, maximum page 10,000. Without status, archived accounts are included; the UI initially filters ACTIVE. The response is `{items,page,size,total}`.

`GET /api/v1/accounts/{id}` returns one scoped account.

`PATCH /api/v1/accounts/{id}` requires version and accepts optional name, institution and status:

```json
{"status":"ARCHIVED","version":0}
```

A stale version returns 409. No financial entry is produced by metadata changes or archival. There is no account deletion endpoint.

`GET /api/v1/accounts/{id}/ledger?page=0&size=20` returns a bounded `{items,page,size,total}` history with id, kind, amount, currency, bookingDate, createdAt and balanceAfter.

## Local validation

```sh
docker compose up --build -d backend frontend
```

Open a workspace, create an account with BRL 1500.00, inspect its history and verify one opening entry. Edit the name, archive it, select the archived filter and verify the balance remains 1500.00. No volume deletion or seed reset is needed; Flyway adds V3 to the existing database.

Automated checks are in AccountIntegrationTest and frontend/e2e/accounts.spec.cjs. Do not regard this increment as release-ready until those pass. Transaction entry, reconciliation, attachments, icons/colors and full reporting are outside this first accounts increment.
