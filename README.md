# FinanceHub

Personal and small-business finance platform under construction. **The foundation and the first accounts increment are in source; the full application is not complete or production-ready.** Backend compilation and PostgreSQL integration tests now pass. See [implementation status](docs/IMPLEMENTATION_STATUS.md) for validation results and remaining gates.

## Current increment

- Java 21 / Spring Boot backend, PostgreSQL/Flyway, Redis configuration, JSON logs and Actuator.
- Native registration, mandatory email verification, login, password recovery/change, refresh rotation/reuse detection, logout, profile and session APIs.
- RS256 JWTs with issuer, audience and key ID; database checks for suspension and session revocation on each protected request.
- PostgreSQL email outbox with retrying SMTP delivery and database-backed authentication rate limits.
- Workspace creation, paginated listing and membership-scoped lookup, with a Hibernate workspace entity.
- AngularJS 1.8.3 registration/login/recovery/workspace components connected to those APIs, Portuguese/English/Spanish labels, light/dark themes and a restrictive NGINX CSP.
- Financial accounts with immutable opening entries, ledger-derived balances, idempotent creation, scoped pagination, metadata editing/archive and a connected AngularJS account page.
- Exact monetary, installment-allocation and billing-calendar policies. Income/expense, transfer and persisted card workflows remain pending.

## Local development

Requirements: Docker with Compose, network access for images and Maven/npm packages.

```sh
cp .env.example .env
# Set a unique DATABASE_PASSWORD in .env.
docker compose up --build -d
```

Local services:

- App: http://localhost:8081
- Development email inbox: http://localhost:8025
- Backend health: http://localhost:8080/actuator/health
- Local API description: http://localhost:8080/v3/api-docs

Register in the app, open the verification email in Mailpit, confirm the link and log in. Then create a personal or business workspace. No credentials or administrator accounts are seeded. Compose creates a development RSA key pair once in a Docker volume and reuses it across restarts. Mailpit, local HTTP cookies and generated keys are for local development only.

Container image builds package source without running Testcontainers inside the build container. Run the verification suite separately before using a built image.

## Verification

```sh
sh scripts/check-domain.sh
mvn -B -f backend/pom.xml verify
cd frontend
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm test
npm run build
```

Actual results: **79,737 financial assertions**, **3,009 identity assertions**, **29 Maven tests** (including PostgreSQL migrations, identity and accounts), frontend service/formatting tests, JavaScript syntax checks and the production asset build passed. See [implementation status](docs/IMPLEMENTATION_STATUS.md) for browser results and remaining validation.

The frontend lockfile is included; Docker and CI use `npm ci`. The npm audit reports two affected packages (one high, one moderate) associated with AngularJS. This remains a release gate; see [security status](SECURITY.md).

## Documentation

[Architecture](ARCHITECTURE.md) · [API](API.md) · [Authentication](docs/AUTHENTICATION.md) · [Financial rules](FINANCIAL_RULES.md) · [Security](SECURITY.md) · [Testing](TESTING.md)

Income/expenses, transfers, reconciliation, persisted cards/invoices, reports, exports, budgets/goals, financial dashboards, Kubernetes/Helm, production CI/CD, monitoring dashboards and synthetic data remain outstanding.

After rebuilding both app images, select a workspace to open its accounts. See [account contracts and invariants](docs/ACCOUNTS.md). The real-browser scenario can be run with `npm run test:e2e` from `frontend/` after installing Chromium with `npx playwright install chromium` and starting Compose.
