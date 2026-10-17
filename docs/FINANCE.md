# Categories, bills and financial analysis

Select a workspace, then use the navigation above its accounts to open **Categorias**, **Débitos** or **Análise financeira**. Create an active category and account before registering a bill. Forms, analysis and navigation support Portuguese, English and Spanish and both themes.

## Financial behavior

- Bills represent accounts payable. Creation records a positive, exact amount in the selected account's currency and leaves its balance unchanged.
- Payment records one immutable negative `DEBT_PAYMENT` entry and marks the bill paid in the same transaction as the audit and idempotency receipt. The payment date must be on or after account opening and no later than today in the workspace timezone.
- Payments require an active account with sufficient current funds. This increment does not support overdrafts or partial payments. Backdated payments are allowed; the funds check uses the current balance, not a reconstructed historical balance.
- Pending bills can be cancelled without affecting balances. Paid bills cannot be cancelled or edited. Payment reversals, recurring bills and installments remain outside this increment.
- Categories can be renamed, archived and reactivated. Names are unique within a workspace ignoring case. Archived categories remain visible in history and reports; existing bills can still be paid, but new bills require an active category.
- All mutations acquire the existing workspace write lock and recheck authorization after locking. This serializes competing bill payments and account updates. Only financial-write roles may mutate; read-only members can view the analysis.

## API

All routes are under `/api/v1`, require bearer authentication and `X-Workspace-Id`, and use the existing Problem Details error format. Lists return `{items,page,size,total}`, with page 0 by default, size 20, maximum size 100 and maximum page 10,000.

| Method and route | Request | Result |
| --- | --- | --- |
| `GET /categories` | page, size | Paginated categories, including archived |
| `POST /categories` | name | 201; original response replay: 200 |
| `PATCH /categories/{id}` | optional name/status, required version | Updated category; stale version: 409 |
| `GET /debts` | optional status (`PENDING`, `PAID`, `CANCELLED`), page, size | Paginated bills ordered by due date and ID |
| `POST /debts` | accountId, categoryId, description, amount (decimal string), dueDate | 201; original response replay: 200 |
| `POST /debts/{id}/payments` | paidDate | 200, paid bill |
| `POST /debts/{id}/cancellation` | empty object | 200, cancelled bill; repeated cancellation is harmless |
| `GET /dashboard` | from, to (ISO dates) | Analysis described below |

Category creation, bill creation and payment require a UUID `Idempotency-Key`. Receipts are scoped by workspace, actor, operation and key. Identical retries replay the original response; changed payloads return 409. Monetary values are normalized before comparison. Repeated payment of an already paid bill with the same payment date is also harmless with a different key. The browser preserves keys after failed responses and does not automatically replay mutations.

Foreign-workspace references return 404, read-only writes return 403, invalid DTOs return 400, state/version conflicts return 409, and semantic violations (precision, amount, archived references, insufficient funds, invalid payment date or period) return 422. Amounts and aggregates are decimal strings; the browser does not use floating-point arithmetic to format money.

## Reading the analysis

`from` and `to` are inclusive, span at most 366 days and cannot end in the future. The response has:

- `totals`, one row per account currency: current ledger balance, all pending bills, overdue pending bills, payments within the selected period, previous-period payments, balance minus all pending bills, shortfall flag and spending change percentage.
- `categories`: paid spending and each category's percentage within its own currency, ordered by amount. Category renames are reflected in reports.
- `months`: payments grouped by month and currency, including zero months. First and last months include only the selected dates.
- `asOf`: today's date in the workspace timezone. Overdue means a pending due date strictly before this date.

The comparison period has the same number of days and immediately precedes `from`. Percentage change is null when previous spending is zero. Account balances include archived accounts. Pending amounts include all due dates, including dates outside the selected period. The screen labels this difference explicitly. Reads use one repeatable-read snapshot.

Currencies are never summed or converted. The balance-after-pending indicator is a simple coverage comparison, not a cash-flow forecast: it does not model future income, transfers or credit facilities. Spending currently includes only bill payments; income entry, profitability, savings-rate analysis and card/invoice workflows remain pending.

## Verification

Run `mvn -B -f backend/pom.xml verify` with Docker available for PostgreSQL integration tests. The finance tests cover payment and cancellation, monetary precision, dates, archived categories, replay, concurrent overspending, workspace isolation, read-only roles, audit rollback, monthly/category totals and multi-currency comparisons.

Run `npm test`, `npm run lint` and `npm run build` from `frontend`. With local Compose running, `npm run test:e2e -- finance.spec.cjs` exercises account/category/bill creation, payment, cancellation, analysis, language/theme switching and the ledger on desktop and mobile Chromium. It creates synthetic users and financial records in the local database.
