# FinanceHub

Personal and small-business finance platform under construction. **Phase 1 source is implemented in part; the full application is not complete or production-ready.** Spring/container and browser integration have not run in the current environment.

## Current increment

- Java 21 / Spring Boot backend, PostgreSQL/Flyway, Redis configuration, JSON logs and Actuator.
- Native registration, mandatory email verification, login, password recovery/change, refresh rotation/reuse detection, logout, profile and session APIs.
- RS256 JWTs with issuer, audience and key ID; database checks for suspension and session revocation on each protected request.
- PostgreSQL email outbox with retrying SMTP delivery and database-backed authentication rate limits.
- Workspace creation, paginated listing and membership-scoped lookup, with a Hibernate workspace entity.
- AngularJS 1.8.3 registration/login/recovery/workspace components connected to those APIs, Portuguese/English/Spanish labels, light/dark themes and a restrictive NGINX CSP.
- Exact monetary, installment-allocation and billing-calendar policies. Financial workflows are not yet implemented.

## Local development

Requirements: Docker with Compose, network access for images and Maven/npm packages.

```sh
cp .env.example .env
# Set a unique DATABASE_PASSWORD in .env.
docker compose up --build -d
```

Expected services, **pending runtime verification**:

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
npm install --ignore-scripts --no-audit --no-fund
npm run lint
npm test
npm run build
```

Actual results: **79,737 financial assertions**, **3,009 identity assertions**, **four frontend service tests**, JavaScript syntax checks and Compose configuration validation passed. Maven and npm installation failed at DNS resolution. Docker daemon access is denied. Full backend compilation, migrations, JUnit/Testcontainers, asset build, browser rendering and E2E remain unverified. See [implementation status](docs/IMPLEMENTATION_STATUS.md).

A frontend lockfile must be generated and audited when npm access is available; the current Docker/CI install uses exact direct dependency versions but is not fully reproducible for transitive packages.

## Documentation

[Architecture](ARCHITECTURE.md) · [API](API.md) · [Authentication](docs/AUTHENTICATION.md) · [Financial rules](FINANCIAL_RULES.md) · [Security](SECURITY.md) · [Testing](TESTING.md)

Complete phase 1 verification before adding dependent ledger workflows. Accounts, income/expenses, transfers, persisted cards/invoices, reports, exports, budgets/goals, financial dashboards, Kubernetes/Helm, production CI/CD, monitoring dashboards and synthetic data remain outstanding.
