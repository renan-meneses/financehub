# Testing

## Executed

- `sh scripts/check-domain.sh`: **79,737 financial assertions** and **3,009 identity assertions** passed using Java 21 directly. Financial checks cover exact minor-unit allocation and calendar boundaries. Identity checks cover password byte limits, normalization, SHA-256 known vector, token shape and uniqueness sampling.
- `cd frontend && npm run lint`: JavaScript syntax and prohibited dynamic-execution patterns passed. This is a small source checker, not a full ESLint ruleset.
- `cd frontend && node test/auth.test.cjs`: **4 authentication tests passed**, covering shared refresh promises, failure recovery, logout error handling, bearer scoping and no automatic mutation replay. Tests execute the actual Auth/interceptor source with mocked HTTP; they are not browser or backend integration tests.
- Compose configuration validation passed with a temporary database password.
- Development RSA key generator executed successfully in `/tmp`.

## Backend verification (2026-10-17)

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

`mvn -B -f backend/pom.xml verify` passed: 29 tests, zero failures, errors or skips. This includes three migration tests, seven account integration tests and ten identity integration tests using real PostgreSQL Testcontainers. The expired-JWT fixture now sets issuance before expiration so the encoder can construct the token and the request reaches the rejection assertion. Testcontainers deliberately does not skip tests when Docker is missing.

`npm ci --ignore-scripts --no-audit --no-fund`, frontend tests, lint and the production asset build passed. The generated lockfile is used by CI and Docker. `npm audit --json` completed with two affected packages (one high, one moderate) associated with AngularJS; see [security status](SECURITY.md). Full keyboard accessibility and additional error-state coverage remain pending. Maven, npm network access, Docker and Chromium required execution outside the restricted agent sandbox.

## Phase gates

1. Foundation: build, migrations, native auth/JWT/rate-limit tests and browser login/workspace smoke passed; add SMTP worker failure/retry tests and complete remaining authorization gates.
2. Core: ledger rollback, tenant isolation, transfers, pagination and connected UI workflows.
3. Cards: purchase/refund/payment conservation, concurrent idempotent payments, cash/accrual double-count prevention.
4. Analytics: JPQL fixture totals, filtered exports, formula injection and chart navigation.
5. Quality: requested E2E journey, dependency scans, accessibility, query plans and load tests.
6. Infrastructure: container smoke, Helm/Kubernetes validation, staging deployment and backup restoration.

CI runs Maven verification separately from image builds. Dockerfiles package applications without attempting Docker-in-Docker Testcontainers execution.

## Accounts increment

`node test/accounts.test.cjs` passed 3 additional tests: idempotency-key reuse after lost responses, isolated workspace headers/requests and exact formatting of large monetary values. No monetary values are converted through JavaScript Number.

`AccountIntegrationTest` adds seven PostgreSQL-backed cases covering opening precision, immutable entries, idempotency/replay/conflicts, concurrent creation, tenant isolation, read-only denial, archive/version behavior, rollback on audit failure, future-date rejection and tenant foreign keys. All seven passed on 2026-10-17.

The shared Spring/Testcontainers fixture closes each test context after its class and suppresses MockMvc request dumps to avoid printing authentication payloads.

`frontend/e2e/accounts.spec.cjs` performs real registration, verification through Mailpit, login, refresh restoration, workspace/account creation, ledger inspection and archival on desktop/mobile Chromium. It does not mock backend calls. Run after Compose starts:

```sh
cd frontend
npm ci --ignore-scripts --no-audit --no-fund
npx playwright install chromium
npm run test:e2e
```

Both desktop and mobile Chromium scenarios passed on 2026-10-17 against the local Compose services after rebuilding and restarting the frontend. The mobile run initially failed because the ledger table expanded its grid column beyond the viewport. Setting `min-width: 0` on grid children confines the table to its scroll container; a new page-width assertion reproduced the overflow before the fix and passes afterward, together with the normal Save click. The frontend image build also passed using `npm ci`.

The extended scenario also passed on desktop and mobile Chromium on 2026-10-17 (2 tests, 14.7 seconds). It verifies disabled empty-login submission, Tab focus from email to password to submit, keyboard submission, incorrect-password feedback and recovery, invalid monetary input, server rejection of a future opening date (HTTP 422), preservation of the account draft and successful correction without duplicate visible accounts. These checks exercise real backend responses. Full keyboard accessibility, screen-reader behavior and other error paths remain pending.

The scenario uses synthetic per-run accounts. Hosted CI has not been executed here, but declares the same browser gate after backend/frontend verification. Traces are disabled to avoid capturing token/password payloads. The Mailpit lookup follows its [API contract](https://mailpit.axllent.org/docs/api-v1/).
