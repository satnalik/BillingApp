# Bill retries and concurrent customer creation

## What changed

Sales bill creation now requires a UUID `requestKey`. Each submitted bill keeps one key and a fingerprint of its submitted customer, salesman, items, discount and payment details. The key is unique within a tenant and belongs to the cashier who saved the invoice. Legacy invoices keep null keys and are not retroactively matched to new submissions.

Submitting the same key and details returns the existing invoice ID without creating another invoice, payment, customer or stock movement. Recovery occurs before checking current stock, salesman or open shift, so a committed bill can be recovered after stock changes or shift closing. The existing invoice's current state is returned, including later collections, returns or cancellation; it is not a duplicate or a new sale.

Different details under an already committed key receive HTTP 409 with `BILL_REQUEST_CONFLICT`. Another cashier cannot recover/replay a key owned by someone else (403). A different tenant has a separate namespace. A new intended sale needs a new UUID; two intentionally different UUIDs are separate submissions even if their bill contents match.

## Backend transaction protection

`BillSubmissionService` acquires a PostgreSQL transaction-scoped advisory lock derived from the tenant and UUID, then checks for the saved invoice. The lock serializes simultaneous submissions/recovery checks of the same key across backend processes. A hash collision only serializes unrelated requests; lookup and uniqueness still use the full tenant/UUID.

For a new bill, lock order is request key, cashier account/shift, customer contact, then sorted product IDs. Existing bill, payment and stock transaction handling remains in use. The request fields are saved with the invoice in the same transaction. PostgreSQL releases the advisory lock at commit/rollback or connection loss. The database unique constraint is an additional backstop.

The lock uses Spring's `JdbcTemplate` on the same datasource/transaction as JPA. Creation and recovery explicitly use read-committed isolation so a request waiting on another save sees that save's commit after acquiring the lock. It is a PostgreSQL implementation, consistent with the application's runtime database; it is not an in-memory lock or H2-compatible SQL.

## Customer creation

The old lookup-then-insert path was replaced with a tenant-scoped PostgreSQL `INSERT ... ON CONFLICT (tenant_id, contact_number) DO UPDATE`.

If two cashiers create separate bills for the same new contact, PostgreSQL creates one customer and safely reuses that row for the other bill. An existing nonblank customer name is preserved; a blank name can be filled from a later bill. Every invoice still stores its own customer name/contact snapshot. Blank contacts do not create customer rows. Contacts retain the previous trimmed-text identity convention; this change does not merge differently formatted historical phone numbers.

The customer operation belongs to the bill transaction. Failure rolls back customer, bill, payments and stock together. Customer timestamps use the application's local time convention, and this path requires the existing tenant/contact unique constraint.

## Browser recovery workflow

Before sending a bill, the frontend stores its UUID, exact API payload and draft/payment snapshot in browser storage. Recovery is scoped by API address, tenant and cashier account. The UUID uses browser cryptographic randomness, including a fallback supported on plain HTTP LAN addresses.

- A confirmed save opens the existing invoice print/download flow and clears that pending submission.
- A lost response, HTTP 5xx, authentication failure or response without an invoice ID keeps the original submission. **Retry save** sends that exact payload and UUID. **Check saved invoice** looks up its committed invoice.
- Unconfirmed submissions block further editing/saving in that screen. Refreshing or navigating back to New Bill in the same browser/account restores pending submissions.
- A normal server validation rejection (400) or recognized shift/stock conflict restores the editable draft and payment form. The draft keeps its UUID. Bill-creation validation errors now use 400 rather than a generic 500. A committed key with different details remains blocked until its saved invoice is recovered.
- A 404 from the recovery lookup does not delete the submission. Retry the original save. This avoids discarding a request whose save result is still uncertain.
- A synchronous submission guard also prevents rapid repeated clicks/Enter events from issuing independent first submissions.
- Browser storage must be available before sending. Pending records contain customer/cart/payment data but do not store credentials or tokens. Clearing browser storage, changing browser/API origin or ending a private-browsing session can remove access to local recovery records; retain the original UUID when recovering through the API. The invoice and server protection remain in PostgreSQL.

## API

`POST /api/bills` now requires, for example:

```json
{
  "requestKey": "9786e584-f523-4bdc-a764-a5a8ba02f81d",
  "customerName": "Customer",
  "contactInfo": "9876543210",
  "salesmanEmployeeId": "YOUR_SALESMAN_ID",
  "instantDiscountAmount": 0,
  "items": [{"productId": 1, "quantity": 1, "discount": 0, "unitSellingPrice": 100}],
  "payments": [{"method": "CASH", "amount": 100}]
}
```

Use real product/salesman IDs and valid prices/taxes for your store. Keep this exact payload/key when retrying. Swagger/API callers must supply their own UUID. An open shift is required for a new sale; recovery of an existing sale does not require an open shift.

`GET /api/bills/submissions/{requestKey}` returns the committed `BillResponse` for the signed-in tenant/cashier, or 404 when none is found. The lookup waits on the same request lock. It does not create a bill. Existing staff-role access to `/api/bills/**` applies.

This change covers **sales bill creation**. It does not add retry keys to every payment, return or purchase endpoint.

## Rollout and checks

Update backend and frontend together. Restart the backend; local/QA/desktop `ddl-auto=update` adds the two nullable invoice fields and unique constraint. Production using `ddl-auto=validate` must apply [20261008-bill-submissions.sql](migrations/20261008-bill-submissions.sql) before starting the updated backend. No existing invoice is rewritten. The customer constraint already exists in the entity; the migration ensures it exists on older production databases.

Compilation and frontend production build are used for implementation checks. New recovery files are linted separately; `NewBill.jsx` retains its seven pre-existing effect/memoization/unused-function lint findings. Automated tests and live PostgreSQL/browser concurrency workflows have not been run for this change. Concurrent-use release validation and LAN sharing remain separate release work. The executable has not been rebuilt.
