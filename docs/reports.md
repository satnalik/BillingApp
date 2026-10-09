# Reports for the initial store release

Open **Reports** as an administrator or manager. Product Sales, Sales Profit, Customer Outstanding and Inventory Reports are now active. Sales Summary and Bill Register continue to use their existing services.

Reports read existing invoices, product records and stock movements. They do not store another set of balances or introduce another collection/adjustment workflow. Stock changes, payment collections and shift operations open their existing screens.

## Product Sales

- Defaults to the last 30 calendar days, including today. Both date endpoints are inclusive.
- Filter by product name, barcode, exact category name or selected salesman, including inactive salesmen for historical invoices.
- Shows net quantity, distinct bill count, taxable item amount, GST, item revenue and current stock. CSV also includes product ID, barcode, category, HSN, gross item value, item discounts and average unit revenue.
- Click stock to open that product in Inventory.
- Cancelled invoices are excluded. Returned quantities reduce sales against the original invoice date, including returns processed later. Fully returned lines disappear from this report.
- **Item revenue is before the final bill-level instant discount.** It can differ from invoice totals in Sales Summary/Bill Register. No allocation of that final discount or cost-at-sale/profit calculation is introduced.
- Category/barcode/stock may reflect current product metadata. Product/name combinations remain separate when a product name changes on historical invoices.
- The report now validates date order, requires an explicit tenant, reads totals and rows in one repeatable-read transaction, and refreshes current stock instead of caching it. A left join includes invoices without a selected salesman when no salesman filter is applied.

## Sales Profit

Daily/date-range estimated gross profit uses product cost captured on each new invoice line. It includes final bill discounts, excludes recorded GST, reverses returned quantities and discloses missing historical costs. Includes product profit/margin rankings, cost-coverage and loss-sale filters, daily totals, CSV and print exports. See [Sales Profit](sales-profit.md) for costing, date and migration details. Product Sales remains a revenue report; Sales Profit provides the separate cost analysis.

## Customer Outstanding

- Uses current positive `Bill.dueAmount`, excluding cancelled invoices, without recalculating a parallel balance ledger.
- Groups invoices by recorded contact. Phone formatting spaces, dots, brackets and hyphens are ignored; country prefixes remain significant. Other contact text is trimmed and compared without case. Missing/placeholder contacts, including unusable short phone values, keep each invoice separate. Names alone never merge customers.
- Names/contact details are snapshots on invoices because bills currently have no customer foreign key. A contact group uses its latest outstanding invoice for the display name, while invoice details retain each original name. Shared contacts can represent multiple people; the report presents contact/invoice groups rather than claiming unique customer IDs.
- Buckets: **0–30**, **31–60**, **61–90**, **over 90 days**, and **unknown date**. Ages use the backend's local calendar date and invoice creation date. A future-dated invoice is placed in the youngest bucket.
- These are **bill ages**, not contractual overdue dates. There is no historical "as of" balance reconstruction.
- Search customer/contact/invoice and select an age bucket. Summaries, exports and the oldest invoice date use only the matching unpaid invoices. Unknown-date balances still contribute to the total and are disclosed separately.
- Expand **View bills**, then **View / Collect**, to use the existing invoice/payment screen. **Open dues collection** opens the existing dues workflow.
- Export either grouped balances or the complete matching unpaid invoice list to CSV. Grouped balances can also be printed or saved as PDF.

## Inventory Reports

### Stock on hand

- Reads current products scoped explicitly to the tenant. Filter product/name/barcode/ID, category, supplier or stock status.
- The low-stock threshold defaults to 10 and can be changed for this report. It is not a saved reorder policy. Zero stock is reported separately from positive low stock.
- Estimate = current stock quantity × current product `costPrice`, rounded to two decimals per product. The total sums those rounded values.
- A missing, negative or non-finite cost, or negative/non-finite stock, leaves value unavailable and is excluded from the estimate. Zero cost is valid. Null stock follows the application's existing zero-stock convention.
- The screen flags unvalued products and stock anomalies. This estimate is not historical/FIFO valuation, selling value, or profit. Different products can have different quantity units.
- Product links open Inventory. Adjustments and opening-stock imports are performed there.

### Stock movements

- Defaults to the last 30 days. Filter by date, product and movement type.
- Reuses the same tenant/product/type/date specification as the existing Inventory history. Includes before/change/after, reason, operator and reference. CSV additionally includes source filename, notes, barcode and operator ID.
- Invoice/purchase reference links open the existing document. Product links open its operational stock history.
- Entries are recorded historical quantity snapshots; the report does not reconstruct historical cost valuation. Balance brought forward entries mark legacy stock initialization.

## Cashier Shift Reconciliation

The former Day End Closing tab now has this name because shifts can cross midnight. Its report mode shows the existing saved shift history, reconciliation and exports. **Manage cashier shifts** opens the existing operational screen for opening shifts, recording cash in/out or counting/closing. Report mode does not display those mutation forms. See [cashier-shifts.md](cashier-shifts.md).

## Shared report controls

- Click a table heading to sort; tables show 25 rows per page.
- CSV and **Print / Save PDF** include all matching rows in the selected sort order, not just the displayed page. Filter descriptions, load timestamps, totals and calculation notes accompany exports.
- Print uses the browser's print dialog; select **Save as PDF** to download a document. There is no additional PDF/export dependency.
- CSV preserves Unicode and quotes untrusted text, including protecting formula-like cells. Numeric columns remain numeric. Print content is inserted as text, not interpreted as HTML.
- Requests are cancelled on report/filter changes; exports are available only for a successfully loaded dataset. Refresh explicitly reloads current data.
- Complete matching datasets are loaded for the three basic report views, including movement history, and pagination is in the UI. Large installations should use focused date/product filters; future server-streamed exports can keep the same calculations and filters. No silent export row cap is applied.

## API and rollout

Existing `GET /api/reports/product-sales?from=2026-10-01&to=2026-10-08&productName=...&barcode=...&category=...&salesmanId=...` is reused.

New read-only endpoints, protected by the existing manager/admin Reports policy:

- `GET /api/reports/customer-outstanding`: `{generatedAt, ageDate, items}` with contact groups and nested unpaid invoices.
- `GET /api/reports/inventory-stock`: `{generatedAt, items}` with current quantity/cost/estimated value and stock status.
- `GET /api/reports/stock-movements?from=2026-10-01&to=2026-10-08&productId=1&type=ADJUSTMENT`: `{generatedAt, items}` for all matching stock movements. All parameters are optional and date limits are inclusive.

No new database tables or migration are required for these reports. Restart the backend and refresh the frontend to use them. The Windows executable has not been rebuilt.

Implementation checks: Java compilation, frontend production build and targeted ESLint. No automated tests or live database/browser workflow checks were run for this change.
