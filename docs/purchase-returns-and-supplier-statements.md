# Purchase returns and supplier statements

## Using the screens

Administrator and Manager users can use these features. Cashiers have no access.

1. Open **Purchases → View → Returns & supplier credit → Return items**.
2. Enter the quantities physically sent back to the supplier, in stock units, and a reason.
3. Confirm. The original invoice stays intact; the return has its own PR-number, timestamp, operator and item snapshots.
4. If the purchase has supplier credit, **Record refund received** records money actually received. Choose cash, UPI or card and optionally add a reference.
5. Open **Suppliers → Statement**, or **Supplier statement** on a purchase. Filter dates, export CSV, or print/save as PDF using the browser print dialog.

Purchased, returned and remaining quantities appear on the purchase detail. The list identifies partial/full returns and outstanding credit. Movement history includes **Purchase return** with a link to the purchase.

## Amounts and stock

- Partial and full returns are supported. Each item uses the original purchase line ID, so duplicate product lines remain separate.
- Returned quantity cannot exceed the unreturned purchased quantity or drive current stock negative. All affected products are locked in ID order and the full return commits atomically.
- Return credit includes bill-level discount and tax in proportion to the original line amounts. Zero-value invoices use quantities as weights. Cumulative decimal rounding ensures a full return credits exactly the original total, even across multiple partial returns.
- Original subtotal, discount, tax, total and gross payments are preserved.
- Net purchase = original total − return credits.
- Due = max(0, net purchase − gross payments + refunds received).
- Supplier credit = max(0, gross payments − refunds received − net purchase).
- Example: total ₹1,000, paid ₹800, return credit ₹300 gives net ₹700, due ₹0 and supplier credit ₹100. Receiving a ₹100 refund clears that credit. A return does not record a cash refund automatically.
- Credits stay against their purchase bills until refunded. Automatic allocation to another purchase is not included. The supplier statement shows the combined net balance as well as separate current unpaid and credit amounts.
- Purchases with returns cannot be cancelled; return the remaining items instead. Paid purchases retain the existing cancellation restriction.
- Barcode label generation uses remaining received quantities. Existing label previews are cleared after a return/refund.
- Existing cost and selling prices are not reverted by a return.

## Statements

The ledger contains original purchases (increase payable), supplier payments (decrease), purchase returns (decrease), refunds received (increase), and cancellation reversals (decrease). Positive balance means payable to the supplier; negative balance means supplier credit.

Purchase entries use the supplier invoice date. Payments, returns, refunds and cancellations use their recorded timestamps. Date boundaries are inclusive. Entries before the start date form the opening balance. Blank dates show recorded history through today. No unrecorded historical supplier balance is inferred.

The table pages 50 entries in the UI. Export and print contain all entries in the selected period, plus opening and closing balances. Current unpaid/credit summaries cover all dates; they are labelled separately from the period balances. CSV text cells are protected against spreadsheet formula interpretation; print content is inserted as plain text.

## API

- `POST /api/purchases/{id}/returns`
  ```json
  {
    "requestKey": "d9a3b231-fad6-4bb8-8076-ab613c2b1639",
    "reason": "Damaged delivery",
    "items": [{"purchaseItemId": 42, "quantity": 2, "expectedReturnedQuantity": 0}]
  }
  ```
- `POST /api/purchases/{id}/refunds`
  ```json
  {
    "requestKey": "022b3172-f2d8-4a82-b551-0b7727c9d217",
    "amount": 100,
    "expectedCreditAmount": 100,
    "method": "CASH",
    "reference": "Cash received from supplier"
  }
  ```
- `GET /api/suppliers/{id}/statement?from=2026-10-01&to=2026-10-31`

Requests require authentication and current tenant context. New mutation lookups explicitly scope the purchase to that tenant. A UUID request key prevents duplicate return/refund submissions; reuse it only for retrying identical details. Changed quantities or credit require refreshing the purchase. Quantity and amount fields accept at most two decimal places. Return reason is required, maximum 500 characters.

## Deploying the source changes

Restart the backend before using the updated frontend. Local, QA and desktop profiles use `ddl-auto=update`: Hibernate adds the new tables/columns, and the startup upgrade expands any legacy PostgreSQL stock movement enum constraint. Existing null return/refund fields are treated as zero/false.

For production (`ddl-auto=validate`), first apply [the SQL migration](migrations/20261008-purchase-returns.sql) to the intended database. Apply the previous stock movement migration first if necessary. No customer database migration was executed during implementation.

The executable must be rebuilt separately when requested; the existing EXE does not contain these source changes.
