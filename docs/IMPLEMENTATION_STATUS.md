# Implementation status

**Backend verification and frontend build passed on 2026-10-17.** The foundation and first accounts increment are implemented, with remaining quality and security gates listed below. This does not mark the full application production-ready.

## Current source

- Native identity routes: registration, verification/resend, login/logout, refresh rotation/reuse revocation, forgot/reset/change password.
- Signed RS256 tokens, mounted key ring, protected CSRF refresh cookie and live session/suspension checks.
- Profile editing with versions, bounded session listing/revocation and workspace membership isolation.
- PostgreSQL rate limits, email outbox and SMTP worker; V2 migration adds their tables and session relationships.
- Hibernate workspace entity, workspace create/list/detail, DTOs and consistent errors.
- AngularJS access and workspace pages connected to these endpoints, three languages, themes, route guards, memory-only bearer service, same-origin NGINX.
- Compose development key initialization and Mailpit, backend/frontend images, frontend verification CI.

## Accounts increment

V3 adds accounts, immutable opening ledger entries and idempotency receipts. Endpoints support scoped listing/filtering, opening, metadata changes, archival and balance history; AngularJS connects these workflows. All seven account integration cases passed against PostgreSQL.

## Actual checks

| Check | Result |
| --- | --- |
| Java domain harness | Passed 79,737 financial + 3,009 identity assertions |
| Frontend source checks | Passed |
| Frontend service/formatting tests | 7 passed using Node, HTTP mocked for service tests |
| Compose configuration | Passed |
| Local development key generator | Ran successfully |
| Maven verify | Passed compilation, packaging and 29 tests; zero failures/errors/skips |
| npm installation/build | Passed npm ci and asset build; lockfile generated |
| npm audit | Two affected packages: one high, one moderate, associated with AngularJS |
| Docker daemon | Accessible outside sandbox; PostgreSQL Testcontainers passed |
| Git | Existing repository and staged work readable; no commit created in this continuation |

The initial sandbox restrictions were resolved by approved execution outside the sandbox. The JWT expiration test fixture needed an issuance timestamp before its expiration; the corrected suite passed. CI and the frontend Dockerfile now use the generated lockfile with `npm ci`. A new container image build and hosted CI execution are not claimed.

## Next execution

1. Complete browser validation and additional error-state/keyboard accessibility checks.
2. Resolve AngularJS dependency advisories and run backend dependency scanning.
3. Add SMTP failure/retry tests and complete membership/admin authorization and remaining phase 1 quality gates.
4. Verify container image builds and hosted CI, then review changes before committing and progressing to categories, income/expenses and transfers.

Remaining scope includes remaining financial schemas/workflows, reconciliation/transfers, persisted card/invoice management, analytics, exports, budgets/goals, jobs, Redis behavior, complete dashboard, membership management, administrative provisioning, optional MFA, Kubernetes/Helm, full CI/CD, tracing/monitoring dashboards, synthetic financial data and remaining deployment documentation.
