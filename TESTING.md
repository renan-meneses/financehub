# Testing

## Executed

- `sh scripts/check-domain.sh`: **79,737 financial assertions** and **3,009 identity assertions** passed using Java 21 directly. Financial checks cover exact minor-unit allocation and calendar boundaries. Identity checks cover password byte limits, normalization, SHA-256 known vector, token shape and uniqueness sampling.
- `cd frontend && npm run lint`: JavaScript syntax and prohibited dynamic-execution patterns passed. This is a small source checker, not a full ESLint ruleset.
- `cd frontend && node test/auth.test.cjs`: **4 tests passed**, covering shared refresh promises, failure recovery, logout error handling, bearer scoping and no automatic mutation replay. Tests execute the actual Auth/interceptor source with mocked HTTP; they are not browser or backend integration tests.
- Compose configuration validation passed with a temporary database password.
- Development RSA key generator executed successfully in `/tmp`.

## Written but not executed

`mvn -B -f backend/pom.xml verify` includes JUnit domain tests, PostgreSQL migration tests and Spring Boot MockMvc identity integration tests. Integration scenarios cover:

- Verification requirement and challenge single use.
- Refresh reuse invalidating descendants and access tokens.
- Concurrent refresh: one rotation succeeds, then detected replay revokes the family.
- CSRF rejection and unauthenticated access.
- Workspace isolation and invalid creation rollback.
- Logout, suspension, password reset and reset replay.
- Optimistic profile updates and cross-user session revocation denial.
- Signed JWT rejection for incorrect issuer/audience, expired or missing expiration.
- Rate-limit failures committing their counters.

Maven fails before compilation because Maven Central DNS is unavailable. Docker daemon access is denied. Testcontainers deliberately does not skip these tests when Docker is missing. No passing integration, migration, JUnit or Spring runtime result is claimed.

`npm install` fails resolving registry.npmjs.org. The production asset build, real AngularJS runtime, rendering at mobile/desktop sizes, keyboard accessibility and full registration-to-workspace browser journey remain unverified. Generate and audit a package-lock.json when installation works, then change CI/Docker to npm ci.

## Phase gates

1. Foundation: execute full build, migrations, native auth/JWT/rate-limit tests; add SMTP worker failure/retry tests and run browser login/workspace smoke.
2. Core: ledger rollback, tenant isolation, transfers, pagination and connected UI workflows.
3. Cards: purchase/refund/payment conservation, concurrent idempotent payments, cash/accrual double-count prevention.
4. Analytics: JPQL fixture totals, filtered exports, formula injection and chart navigation.
5. Quality: requested E2E journey, dependency scans, accessibility, query plans and load tests.
6. Infrastructure: container smoke, Helm/Kubernetes validation, staging deployment and backup restoration.

CI runs Maven verification separately from image builds. Dockerfiles package applications without attempting Docker-in-Docker Testcontainers execution.
