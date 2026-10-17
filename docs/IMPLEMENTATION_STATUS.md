# Implementation status

**Phase 1 remains in progress and not validated.** Native identity and the initial AngularJS/workspace flow have been added in source. Later financial application phases have not started; standalone card policies establish financial design only.

## Current source

- Native identity routes: registration, verification/resend, login/logout, refresh rotation/reuse revocation, forgot/reset/change password.
- Signed RS256 tokens, mounted key ring, protected CSRF refresh cookie and live session/suspension checks.
- Profile editing with versions, bounded session listing/revocation and workspace membership isolation.
- PostgreSQL rate limits, email outbox and SMTP worker; V2 migration adds their tables and session relationships.
- Hibernate workspace entity, workspace create/list/detail, DTOs and consistent errors.
- AngularJS access and workspace pages connected to these endpoints, three languages, themes, route guards, memory-only bearer service, same-origin NGINX.
- Compose development key initialization and Mailpit, backend/frontend images, frontend verification CI.

## Actual checks

| Check | Result |
| --- | --- |
| Java domain harness | Passed 79,737 financial + 3,009 identity assertions |
| Frontend source checks | Passed |
| Frontend HTTP-service tests | 4 passed using Node with mocked HTTP |
| Compose configuration | Passed |
| Local development key generator | Ran successfully |
| Maven verify | Failed before compilation: Maven Central DNS failure |
| npm installation | Failed: npm registry DNS failure |
| Docker daemon | Permission denied |
| Git | Not a usable repository; mounted metadata remains read-only |

No commits could be created. Integration tests, backend compilation, asset build, browser/E2E and container startup are not claimed as successful. Source review does not replace those checks.

## Next execution

1. Restore Maven/npm access, Docker daemon access and a writable Git checkout.
2. Run backend verify and frontend install/build; fix compilation or integration failures. Generate/audit the npm lockfile and use npm ci.
3. Run the documented registration → email confirmation → login → workspace browser journey, including error and mobile states.
4. Execute JWT negative/auth rate-limit tests, add SMTP retry/browser tests and complete membership/admin authorization and remaining phase 1 quality gates.
5. Commit only validated increments, then begin account ledger, categories and transactions.

Remaining scope includes financial schemas/workflows, accounts/transfers, persisted card/invoice management, analytics, exports, budgets/goals, jobs, Redis behavior, complete dashboard, membership management, administrative provisioning, optional MFA, Kubernetes/Helm, full CI/CD, tracing/monitoring dashboards, synthetic financial data and remaining deployment documentation.
