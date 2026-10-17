# Financial rules

## Implemented policy objects

`Money` requires a supported ISO currency with 0–4 fraction digits. It rejects additional nonzero decimals rather than silently rounding. Addition and subtraction require identical currencies. Negative amounts are supported for reversals; purchase allocation separately requires positivity. No floating-point money is used.

`InstallmentAllocator` accepts 1–120 installments. Divide integer minor units by count, distribute the remainder one unit at a time to the earliest installments, and require each installment to be positive. BRL 100.00 / 3 produces 33.34, 33.33, 33.33. This utility does not persist or assign installments to invoices.

`BillingCalendar` clamps days to the last date of each month. Opening is the day after the previous closing. Purchases dated on closing belong to that cycle; later purchases belong to the next cycle. Due date is the first configured due-day occurrence strictly after closing, optionally advanced from Saturday/Sunday to Monday. Holidays are not yet modeled. Convert instants to the workspace timezone before selecting a cycle. Future installments use consecutive closing months.

## Account persistence increment

Account creation now appends exactly one opening entry, including for zero or negative historical balances, and stores its idempotency response and audit record atomically. Balances are JPQL sums over account ledger entries. Metadata editing and archival never alter ledger entries. See [account rules](docs/ACCOUNTS.md). These persistence operations await integration execution.

## Remaining persistence invariants

- All cash balances derive from immutable signed account-ledger postings, including explicit opening entries. Corrections append reversals, never rewrite settled history.
- Lock affected accounts/cards/invoices in a stable order before mutations. Monetary state and audit events commit in one PostgreSQL transaction.
- Idempotency identity includes workspace, actor, operation, key, and canonical request hash. Same key/different payload conflicts. Store result in the same transaction as postings.
- Transfers create matching opposite cash entries in one currency and contribute neither income nor expense.
- Purchase recognition is independent of invoice cash settlement. Cash basis includes account outflows on invoice payment. Accrual spending includes purchases once and excludes invoice payment as a second expense.
- Initial issuer exposure policy: full purchase amount reserves credit immediately, including future installments exactly once. Payments and refunds release exposure; closing does not. Refund/payment interactions must prevent negative liability or double release.
- Reject currency mismatch, insufficient funds under configured account policy, and invoice overpayment. Recheck these rules under database locks.
- Goal allocation earmarks funds and does not create an external cash expense.

These invariants are design requirements; the policy objects have been independently executed; account persistence is implemented in source but has not passed integration execution here.
