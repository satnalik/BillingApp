# GST accounting module

Implementation date: 8 October 2026. Applies to the Spring backend and the React frontend in `Billing_App_UI/pahal-billing-web`.

## 1. Purpose and release scope

This module captures structured GST records while the store bills customers and receives purchases. It gives the owner and accountant invoice registers, tax components, HSN summaries, purchase input-tax review, dated adjustments and exports.

It supports **ordinary domestic goods transactions for a Regular GST registration**, plus an explicit **Unregistered** sales mode. It stores everything in the application's PostgreSQL database and does not contact the GST portal. Internet access is not needed for calculations, posting or reports.

The report is an accountant working record. It is not a filed GSTR-1/GSTR-3B, a portal-upload file, an electronic credit ledger, or a calculation of final tax payable. Input tax marked eligible is a review decision, not a credit claim submitted to the government.

### Included

| Area | What is available |
| --- | --- |
| Store GST setup | Registration mode, GSTIN, legal identity, state/UT, effective date, invoice prefix and sales price basis |
| Product tax setup | HSN, UQC, tax classification and explicitly entered GST rate |
| Sales | Inclusive/exclusive prices, item discounts, final discount allocation, CGST + SGST/UTGST or IGST, customer GST details and delivery address |
| Documents | Financial-year invoice numbering, thermal receipt, immutable original A4 invoice/bill of supply, linked credit/debit-note PDF |
| Purchases | Supplier GSTIN, supplier state/place of supply, purchase price basis and line tax overrides |
| Input review | Pending, eligible or ineligible decision, review evidence, supplier note confirmation and audit |
| Returns/cancellation | Automatic dated adjustments linked to original structured sales/purchases |
| Manual notes | Documented tax-register adjustments with recovery-safe request keys |
| Reports | Output/input component totals, sales/purchase registers, B2B/B2C filter, outward HSN grouping, notes and exceptions |
| Exports | Register CSV, all loaded invoice lines CSV, HSN CSV, document PDF and report Print/Save PDF |
| Period controls | Administrator lock/reopen of completed months with a reason and audit history |

### Outside this release

- Composition accounting, exports/SEZ, deemed exports, reverse charge, cess and special transaction schemes.
- IRP e-invoicing, IRN/signed QR codes, e-way bills, UIN-specific workflows and government filing APIs.
- GSTR-1/GSTR-3B portal JSON or exact statutory return-table mapping, threshold aggregation and amendment filing.
- Automatic GSTR-2B import/matching, supplier registration-status checks, blocked-credit determination, payment-condition monitoring, apportionment/reversal rules or credit set-off.
- Opening credit balances, tax payments/challans, general ledger/trial balance and accounting-period closing entries.
- Automatic commercial settlement of manual tax-only notes, and automatic aggregate credit-note caps against an original invoice.
- Backfilling historical GST from today's product prices or tax settings.

Use the supported ordinary-goods workflow only. If a store needs one of the excluded statutory workflows, this release alone does not complete that workflow.

## 2. Where to find the module

Open **Accounts** in the main sidebar as an administrator or manager. The route is `/accounts`. The separate administrator route `/settings/gst` opens the same module at GST Settings.

| Section | Use |
| --- | --- |
| GST Working Summary | Net output tax, recorded purchase tax, reviewed input working amounts and unresolved review counts |
| Sales GST | Original sales plus signed sales credit/debit notes |
| Purchase GST | Original purchases, inward notes and purchase cancellations |
| Credit / Debit Notes | Linked adjustments and the manual note form |
| HSN Summary | Outward quantity/value/tax grouped by HSN, UQC, classification, rate and B2B/B2C |
| Exceptions | Legacy records without snapshots and pending input reviews |
| Product Tax Setup | Edit a product's tax metadata without changing its stock or price |
| GST Settings | Store registration and calculation settings |
| Periods | Lock/reopen a completed month |
| Audit | Latest 200 configuration, tax-master, manual-note, review and period-control actions |

Select From/To and **Apply / Refresh**. Dates are inclusive. Initial selection is the current month through today. **Today** selects the current date. Changing the B2B/B2C filter affects registers and HSN views; overview totals remain for the entire loaded date range.

## 3. First-time setup

1. Update and restart the backend and frontend together. See deployment below.
2. As administrator, open **Accounts > GST Settings**.
3. Choose **Regular GST registered** or **Unregistered** according to the actual business.
4. Enter legal business name, address, state/UT and effective date. Regular mode also requires GSTIN matching the store state.
5. Choose a 1-4 letter uppercase invoice prefix, such as `PR`.
6. Choose the sales price basis. If the entered selling prices are final shelf/MRP prices including tax, select **Inclusive**. If GST must be added to those prices, select **Exclusive**. Saving this choice does not rewrite product prices.
7. Open **Product Tax Setup**, or edit products in the product master. Enter HSN, UQC, classification and GST rate for every product used in Regular GST billing.
8. In the supplier master, enter the supplier's actual GSTIN and address. Purchases from an unregistered supplier must not be treated as supplier-charged GST.
9. Resume billing with these settings. New invoices use structured snapshots; earlier invoices are retained in their original form.

### Effective date and changes

- Document dates must be between the configured effective date and the backend's current date. Backdated purchases before the effective date are refused once Regular GST is configured.
- Setup is an explicit cutover. Saving settings does not reconstruct already saved invoices, even when their dates are after the selected effective date.
- GSTIN format, state prefix and checksum are validated offline. This does not verify whether a registration is active or whether the entered identity belongs to that business.
- Once a Regular GST document exists, registration mode, GSTIN, state, effective date and invoice prefix cannot be changed. A different registration needs a separate tenant.
- Legal name, address and sales price basis can change for future documents. Previous snapshots keep their original values.
- Settings have an optimistic version; a stale edit is refused and must be refreshed.
- A store can move from configured Unregistered mode to Regular mode. Existing unregistered receipts remain unregistered and are excluded from Regular GST totals.

### Relationship to Store Setup

Store Setup continues to control presentation details such as phone number and receipt footer. GST Settings control the legal identity used in new tax snapshots. On a configured receipt, legal name/address/GSTIN come from that invoice's snapshot. Keep the two settings consistent; changing presentation settings does not change historical tax documents.

## 4. Product and party data

### Product tax fields

| Field | Meaning / validation |
| --- | --- |
| HSN | Numeric goods classification; this implementation accepts 4, 6 or 8 digits |
| UQC | Unit code such as `PCS`, `NOS`, `KGS` or `LTR`; selected from the supported list |
| TAXABLE | Requires a positive GST rate |
| NIL_RATED | Requires rate zero; retained separately from exempt and non-GST |
| EXEMPT | Requires rate zero |
| NON_GST | Requires rate zero |
| GST rate | UI percentage; API fraction, for example UI `18` = API `0.18` |

The calculator accepts rates from zero to one as an API fraction, with at most six decimal places. Amounts and stock quantities are rounded to two decimal places; GST document money uses PostgreSQL `NUMERIC(19,2)`. Invoice gross value before final discount is capped at INR 1,000,000,000.

The application does not classify products or choose a statutory rate from their names. Acceptance of an HSN length/rate does not establish that it is legally correct. The owner/accountant must choose the correct HSN, required number of digits, UQC, classification and current applicable rate.

Alternate barcodes use their base stock quantity multiplier. They share the product's tax setup; they do not introduce batch-specific tax/pricing/cost records.

### Customers

New Bill includes optional GSTIN, customer address, place of supply and delivery address if different. Regular B2B billing requires customer GSTIN, customer name and address. Unregistered-customer invoices of INR 50,000 or more require name and address in this implementation. Delivery address defaults to the customer address in the tax snapshot if no different address is entered.

Party GSTIN presence determines the working **B2B/B2C** grouping. Place of supply determines the tax split independently; it defaults to the store state and must be selected correctly for the actual transaction. A buyer's home/GSTIN state alone does not determine the place of supply of every store sale.

Customer GST details are invoice snapshots. They are not automatically maintained as a reusable GST customer master in this release. Cashier remains the logged-in user; salesman remains the selected salesman.

### Suppliers and purchases

Supplier identity/GSTIN/address are snapshotted at purchase posting. For a registered supplier, GSTIN supplies its state code. Purchase place of supply defaults to the receiving store state. Purchases have their own inclusive/exclusive price selector, independent of the sales price setting.

Expand **Supplier invoice tax details** on a purchase row to override HSN, UQC, classification or rate for that purchase. Empty fields fall back to the product master. A purchase override does not change that product's future sales tax setup. The purchase Add Product dialog also supports tax metadata.

For an unregistered supplier, structured purchase tax is zero and classification is forced to NON_GST; its input review starts INELIGIBLE. Reverse-charge accounting is not inferred or calculated. Regular purchase lines still require HSN/UQC.

The same registered supplier GSTIN and invoice number cannot be posted twice within the same financial year in the new GST register. This check is serialized with posting and rolls back the duplicate purchase's stock/payment changes. It does not retrospectively identify duplicates among legacy purchases or make all purchase requests generally idempotent.

## 5. Calculation rules

### Tax split

For sales, compare store state with place of supply. For purchases, compare supplier state with place of supply.

- Same state/UT: total GST is split into CGST and SGST/UTGST.
- Different state/UT: the full GST amount is IGST.
- The storage field `sgstAmount` holds the state/UT component. UI and exports label it **SGST/UTGST**.
- Unregistered sales have zero GST, regardless of an old product GST rate.

### Discounts and rounding order

1. Resolve effective base quantity from scanned quantity and barcode multiplier.
2. Apply any sales item discount percentage to entered unit price times quantity; round this value to paise.
3. For exclusive prices, add rounded GST to obtain each gross line value. For inclusive prices, the value already includes GST.
4. Treat the final invoice discount as an absolute reduction of **gross payable amount**.
5. Allocate that discount proportionally across gross line values, using largest remainders in paise and stable line order for ties. Allocations add up exactly to the entered discount.
6. Extract taxable value from each discounted gross line: `gross / (1 + GST rate)`, rounded to paise.
7. GST is the remaining gross-minus-taxable value. Split it into components and sum the stored lines.

For an odd tax paise, CGST receives the rounded half and SGST/UTGST receives the remaining paise. Invoice total equals taxable value plus all three tax components. Final discount is already included in these values; it must not be deducted a second time.

### Examples

| Scenario, one item at 18% GST | Taxable | CGST | SGST/UTGST | IGST | Payable |
| --- | ---: | ---: | ---: | ---: | ---: |
| Exclusive price 100, same state | 100.00 | 9.00 | 9.00 | 0.00 | 118.00 |
| Inclusive price 118, same state | 100.00 | 9.00 | 9.00 | 0.00 | 118.00 |
| Inclusive price 118, different state | 100.00 | 0.00 | 0.00 | 18.00 | 118.00 |
| Gross 118 less final discount 18, same state | 84.75 | 7.63 | 7.62 | 0.00 | 100.00 |

For mixed rates, each line retains its own rate/classification and allocated discount. The module does not apply a blended invoice-wide rate.

The browser requests a debounced preview. A preview never posts stock, a payment or a tax document. Billing/purchase posting recalculates against locked current data on the server. A preview error or pending quote blocks confirmation; previews are not authoritative saved invoices.

## 6. Sales workflow and invoice numbering

Use New Bill normally: select products, customer and salesman, enter tax party details if needed, then choose payments and save. Existing cashier shift and bill request-key rules still apply.

Posting saves the operational bill, stock changes, payments and immutable tax document in the same transaction. A refused GST calculation rolls back the transaction. A successful bill retry with the same UUID and payload returns the existing invoice and original snapshot instead of generating another number.

### Number format

Regular sale example: `PR2627/0000001`.

- `PR`: configured prefix.
- `2627`: financial year April 2026 to March 2027.
- `0000001`: seven-digit sequence for this tenant, document type and financial year.
- Maximum length is 16 characters. Sequence capacity is 9,999,999 documents per type/year.
- Sales credits use `C` plus up to three prefix letters; debits use `D`; unregistered receipts use `R`; internal cancellation records use `X`.
- A receipt prefix is shortened when necessary to avoid matching the configured sale prefix.
- Purchase originals retain the actual supplier invoice number. Manually recorded purchase notes retain the actual supplier note number. Automatic goods-return records initially have an internal reference.

Numbers are allocated inside the tenant's PostgreSQL transaction lock. Failed posting rolls back its document; there is no separately consumed numbering reservation. Bill database IDs remain internal and are still accepted for lookup. Legacy display numbers stay `INV-########`.

### PDFs and barcode lookup

- The normal PDF remains the thermal operational receipt. Its barcode encodes the stored invoice number; Search Bill accepts that number, including the financial-year slash, or an internal/legacy bill ID.
- Search Bill provides **Original A4 tax invoice/receipt** for configured sales. This comes from the original GST snapshot and remains unchanged after returns.
- All non-taxable Regular sale lines produce a Bill of Supply heading. Mixed taxable/non-taxable B2C lines produce an Invoice-cum-Bill of Supply heading. Taxable sales otherwise use Tax Invoice.
- The A4 document includes legal identity, number/date, party details, place of supply, HSN/UQC, classification/rate, component amounts and an authorised-signatory space. It does not create a digital signature, IRN or signed government QR code.
- After returns/cancellation, the thermal print is labelled Updated Receipt and shows current operational amounts. It is not a replacement original tax invoice. The original A4 document plus linked notes are the tax trail.
- Accounts > View/review provides the invoice/note PDF and original reference. A purchase PDF is an internal register copy; retain the actual supplier document.

## 7. Purchases, input tax and costing

Create a purchase with supplier invoice number/date, product quantities, entered unit purchase prices and optional row tax overrides. Select the price basis and correct place of supply. The displayed calculated tax comes from line metadata; leave legacy header tax blank/zero for structured GST. A nonzero manually supplied header tax that disagrees with line GST is refused.

Registered-supplier purchase input starts **PENDING**. Open **Purchase GST > View / review** and choose a status with evidence/reason:

| Status | Effect |
| --- | --- |
| PENDING | Included in recorded purchase GST, excluded from reviewed eligible input, listed as an exception |
| ELIGIBLE | Included in the reviewed eligible input working totals |
| INELIGIBLE | Retained in purchase records but excluded from reviewed eligible input |
| NOT_APPLICABLE | Used for outward sales/receipts |

The accountant must independently evaluate supplier documents, GSTR-2B and all eligibility/reversal conditions. An eligible label does not submit a claim or imply the credit is already available on the portal. No simple `output tax - purchase tax` amount is presented as final tax payable; opening balances, set-off and adjustments are not calculated.

Review metadata is audited and may be changed while the relevant periods are open. Original identity, lines and tax amounts are not edited. A purchase cancellation follows its original purchase's review status; review the original, not the cancellation row. Both affected periods must be open for that paired change.

### Product cost and Sales Profit

For structured purchases, product cost becomes the discounted ex-GST taxable line value divided by base quantity, rounded to six decimals. This is still the latest received cost, not FIFO, weighted average or batch valuation. Ineligible/nonrecoverable input tax and freight are not automatically added to cost.

Sales continue capturing the current unit cost at sale. Profit uses the stored taxable sales value without deducting the final discount again. Historical cost snapshots are not rewritten. The Sales Profit report restates retained quantities on the original invoice date; the GST register records notes by their working date. These reports answer different questions and can differ for a selected period.

## 8. Returns, cancellation and manual notes

### Automatic goods adjustments

- A structured sales return/cancellation produces a negative SALE_CREDIT linked to the immutable original sale/receipt. Existing operational stock, payments and dues continue through the existing return flow.
- A structured purchase return produces a negative PURCHASE_CREDIT linked to the original purchase. Supplier balances/stock use the existing purchase-return workflow.
- Purchase cancellation produces a PURCHASE_CANCEL reversal, rather than deleting the original GST entry.
- Legacy transactions without a tax snapshot continue their existing return behaviour but do not invent tax components or new historical GST documents.

### Cumulative partial-return rounding

For each original line component and each return:

```text
previous cumulative reversal = round(original component * previous returned qty / original qty, 2)
new cumulative reversal      = round(original component * current returned qty / original qty, 2)
this note component          = previous cumulative reversal - new cumulative reversal
```

The note is negative. The retained component is original minus the new cumulative reversal. A full return reverses every original paise exactly, even after several partial returns. Purchase commercial return credit sums these same taxable/CGST/SGST/IGST component reversals.

### Supplier note date versus goods-return date

An automatic purchase-return record is **provisional** until the actual supplier credit note is confirmed. Initially its working date is the store's return date and registered-supplier input status is PENDING. Record the supplier note number/date through input review.

For that automatic return record, the confirmed supplier note date then controls its inward reporting period; its recorded goods-return document date remains unchanged. Reports expose both dates. Changing the confirmation requires the recorded return month, previous reporting month and new supplier-note month to be open. Supplier note dates cannot precede the original invoice or lie in the future. A manually recorded supplier note keeps its own issue date; review cannot move that manual note's date.

This records evidence and period placement; it does not automate statutory credit-reversal timing or eligibility conditions. Reconcile pending goods returns with supplier documents before preparing a return.

### Manual tax-only notes

Use **Credit / Debit Notes > Record a documented credit / debit note** for a genuine documented tax adjustment not already generated by a goods return. Choose an original SALE or PURCHASE within the loaded date range, note type/date, reason and lines.

- Enter a positive **total ex-GST taxable adjustment** per line. Quantity can be zero for a price-only adjustment; it cannot be negative.
- Credit notes are stored negative; debit notes positive.
- The selected original supplies party identity and tax geography. Description/HSN/UQC/rate/classification are entered explicitly for the adjustment.
- A purchase note requires the actual supplier note number. An unregistered supplier cannot be charged with positive GST.
- **Manual notes change only the GST register.** They do not change original bill balances, customer/supplier dues, payments, stock or operational profit. Perform any required commercial settlement separately using an appropriate supported workflow.
- The module does not verify legal adjustment eligibility or cap all cumulative notes against the original invoice. Review amounts and avoid entering an automatic goods-return adjustment again as a manual note.

The browser freezes and stores an unconfirmed note payload with a UUID before posting. If the response is lost, **Retry saved note** sends the same payload. The backend returns the existing note for an identical tenant/key/payload, and rejects reuse with different details. Resolve that saved note before entering another. Clearing browser storage loses the browser recovery record, not a note already saved on the server.

## 9. Reports and accountant exports

The GST register reads snapshots, not today's product master or mutable current bill totals. Signed credits reduce and debits increase their corresponding totals. UNREGISTERED documents are excluded from Regular GST totals.

### Period basis

- Original sale: original posting date.
- Original purchase: supplier bill date entered when posted.
- Sales adjustment: note issue date, normally today's return date for automatic notes.
- Purchase adjustment: note issue date; an automatic purchase-return record uses the confirmed supplier note date when available.
- Purchase cancellation: cancellation posting date.

For example, an October sale returned in November remains a full October GST invoice; November has its credit note. The operational bill may show returned quantities and lower current balances in October-based operational reports.

### HSN grouping

Outward HSN rows are grouped by HSN + UQC + classification + GST rate + B2B/B2C. Credit quantities/values reduce the relevant group. A price-only note with zero quantity changes value/tax without changing outward quantity. Inward HSN reporting is not added in this release.

### Export choices

1. Use a table's **CSV** control for its currently filtered/sorted register, HSN summary or other table.
2. Use **Export all loaded invoice lines CSV** for every line in the selected register/date range and B2B/B2C selection, including document/party/original-reference metadata. This button uses all loaded rows in that selection; it does not inherit the table's text-search or sort state.
3. Use **Print / Save PDF** for the displayed report.
4. Use **View / review > Download document PDF** for an individual invoice/note.

CSV rates labelled GST % are exported as percentages, such as `18`, not API fractions. Monetary numeric cells retain their sign; credit notes are negative. Text cells are quoted and protected against spreadsheet-formula interpretation. No government JSON is generated.

Sales and HSN exports provide working inputs for GSTR-1 preparation. The component summary and reviewed input provide working inputs for GSTR-3B preparation. The accountant must perform statutory table classification, aggregation, adjustments and portal submission separately.

### Exceptions and legacy data

LEGACY_SALE and LEGACY_PURCHASE mean that a transaction has no immutable structured tax snapshot. A legacy purchase's header tax is insufficient to determine line tax/ITC. These records are excluded from GST totals and listed for independent review, even if a current product now has complete metadata.

INPUT_REVIEW means a purchase/adjustment is pending review. Raw purchase tax includes it, while reviewed eligible input excludes it. Provisional purchase-return credits remain pending until the supplier note is confirmed. Do not interpret the exception count as a complete automated legal-compliance check; unsupported workflows and statutory eligibility are not detected automatically.

## 10. Period locks and audit

As administrator, open Periods, select a completed month, choose Lock and enter a review reason. The current/future month cannot be locked. Reopening also requires a reason.

A lock prevents new GST documents dated in that month and review/date-confirmation changes affecting that month. It does not delete records, mark a return filed, certify all exceptions resolved or freeze every POS module. Payments against an existing invoice and later open-period goods returns may still be recorded. A backdated purchase into a locked month is refused.

Audit records retain actor, timestamp, action, reference and details for settings, product tax edits, manual notes, input reviews and lock/reopen actions. Tax documents themselves retain their posting actor/time and original linkage. There is no public edit/delete endpoint for original tax amounts. Direct database administration is outside these application controls.

## 11. Authorization

| Capability | Cashier | Manager | Administrator |
| --- | --- | --- | --- |
| Read GST settings and get sales quote | Yes | Yes | Yes |
| Existing sales posting/search and original sale A4 download | Yes | Yes | Yes |
| Accounts registers, HSN, exports, audit and document PDF | No | Yes | Yes |
| Product tax setup | No | Yes | Yes |
| Purchase quote/posting and input review | No | Yes | Yes |
| Manual tax notes | No | Yes | Yes |
| Save GST settings / lock or reopen a period | No | No | Yes |

Tenant scope comes from the authenticated server context. Supplying another tenant or document ID does not grant access. UI visibility is convenience; Spring Security enforces the API permissions.

## 12. API reference

Base path: `/api`. Requests use the existing authenticated application API configuration.

| Method and path | Purpose |
| --- | --- |
| GET `/gst/settings` | Current tenant settings; NOT_CONFIGURED if none saved |
| PUT `/gst/settings` | Save administrator settings, including current `version` |
| POST `/gst/quote` | Preview using normal CreateBillRequest item fields and optional place of supply |
| POST `/gst/purchase-quote` | Preview using CreatePurchaseBillRequest |
| PUT `/gst/products/{id}/tax` | Save HSN/UQC/classification/fractional rate |
| GET `/gst/report?from=2026-10-01&to=2026-10-08` | Inclusive snapshot register, summary, HSN and exceptions |
| GET `/gst/documents/{id}` | One tenant-scoped tax document with lines |
| GET `/gst/documents/{id}/pdf` | Document PDF |
| PATCH `/gst/documents/{id}/review` | Purchase input review / supplier note confirmation |
| POST `/gst/adjustments` | Manual linked note with UUID request key |
| GET `/gst/periods` | Current period statuses |
| PUT `/gst/periods` | Administrator lock/reopen with reason |
| GET `/gst/audit` | Latest 200 tenant audit events |
| GET `/bills/lookup?number=PR2627%2F0000001` | Find operational bill by stored invoice number |
| GET `/bills/{id}/tax-invoice` | Immutable original sale A4 document |

Product tax request example:

```json
{
  "hsnCode": "1234",
  "unitCode": "PCS",
  "taxCategory": "TAXABLE",
  "gstRate": 0.18
}
```

`1234` is an illustrative shape, not a recommended classification. Use the actual goods HSN.

Input review request example:

```json
{
  "status": "ELIGIBLE",
  "notes": "Original invoice and GSTR-2B checked; eligibility reviewed by accountant.",
  "supplierNoteNumber": null,
  "supplierNoteDate": null
}
```

For a returned purchase's eligible reversal, provide the actual supplier note number and ISO date instead of null.

Manual price-adjustment shape:

```json
{
  "originalDocumentId": 1,
  "requestKey": "63b23bb8-2a28-4280-9371-f36b1d64834f",
  "documentType": "SALE_CREDIT",
  "documentDate": "2026-10-08",
  "reason": "Documented price correction",
  "lines": [{
    "productId": 1,
    "productName": "Original goods description",
    "hsnCode": "1234",
    "unitCode": "PCS",
    "taxCategory": "TAXABLE",
    "quantity": 0,
    "taxableAmount": 10,
    "gstRate": 0.18
  }]
}
```

IDs/dates/HSN/rate must refer to real tenant data. Generate a new UUID per new adjustment; reuse the exact payload only when recovering that adjustment. PURCHASE_CREDIT/PURCHASE_DEBIT also require `documentNumber` for the supplier note. Most validation failures return HTTP 400 with `message`; authorization failures return 403, and existing lock-conflict handling returns 409.

## 13. Storage and implementation map

| Table/entity | Responsibility |
| --- | --- |
| `gst_settings` / GstSettings | One configuration per tenant, optimistic version |
| `gst_documents` / GstDocument | Immutable original identity, source/reference, dates, signed component totals; mutable review evidence |
| `gst_document_lines` | Ordered HSN/UQC/rate/classification/quantity/tax snapshots |
| `gst_periods` / GstPeriod | Unique tenant/month control |
| `gst_audit` / GstAudit | Configuration/review/control actions |
| `bills`, `bill_items` | Operational snapshot fields, stored tax number and allocated final discount |
| `purchase_bill_items` | Original purchase line GST values for consistent return calculation |
| `products` | Master classification and UQC in addition to existing HSN/rate |

`sourceKey` is unique per tenant: `sale:<billId>`, `purchase:<purchaseId>`, `sale-return:<UUID>`, `purchase-return:<returnId>`, `purchase-cancel:<purchaseId>` or `manual:<UUID>`. Original bill IDs and source-line IDs link notes to the correct snapshot. Bills also enforce tenant/stored-tax-number uniqueness; nullable legacy tax numbers remain valid.

- **GstCalculator:** decimal calculation, discount allocation, validation and tax split.
- **GstService:** settings, quotes, transactional posting, returns, manual notes, reviews, numbering and locks.
- **GstReportService:** repeatable-read tenant report with signed totals, period placement, HSN and exceptions.
- **GstPdfService:** original A4 documents; PdfGeneratorService retains thermal printing.
- **GstController:** GST API surface. BillController adds number lookup and original-sale PDF.
- **BillingService/PurchaseService/PurchaseReturnService:** integrate snapshots and adjustments into existing stock/payment transactions.
- **Frontend `src/gst/`:** Accounts sections/settings/manual notes. `useGstQuote.js` handles debounced previews; `GstFields.jsx` supplies shared tax inputs.

### Concurrency and persistence

Posting/settings/review/period operations use a PostgreSQL transaction advisory lock derived from tenant plus a settings-row write lock. Product tax edits acquire the same product row lock as stock changes, so saving tax metadata cannot overwrite another counter's stock update. Existing sale submission locks and sorted product locks remain in place.

Original document fields are not updated by the application; JPA marks header snapshot columns non-updatable. Reviews alter only metadata and are serialized/audited. This is an application audit trail, not a cryptographic tamper-proof ledger or database-level append-only guarantee. Do not directly edit historical snapshots.

## 14. Deployment and operating notes

Update backend and frontend together, then restart both. No executable rebuild is needed for source development; the previously packaged executable still contains its old bundled code until explicitly rebuilt.

- Local, QA and desktop profiles use Hibernate `ddl-auto=update`; restart adds the new mapped tables, nullable fields and constraints.
- Production uses `ddl-auto=validate`. Apply [20261008-gst-accounting.sql](migrations/20261008-gst-accounting.sql) to the intended customer database before starting the updated backend. Apply earlier module migrations first if upgrading an older schema.
- The migration does not backfill invoice amounts/classifications, change product prices, enable GST for any tenant or delete data.
- Database backup/restore must include GST tables together with bills, purchases, stock and payments. Exported CSVs are not a complete application backup.
- Server date/time determines posting dates and financial years; use the intended store timezone. Browser date filters use the workstation's local date.
- No customer database migration, automated tests or live billing/portal workflow was run as part of implementation. Java compilation, targeted frontend lint and production frontend build are the build checks used.
- Targeted lint passes for the GST components and the other changed screens/helpers. NewBill still reports its seven existing React effect/memoization/unused-handler lint findings; this is not a claim that whole-project lint passes. Vite also retains its bundle-size warning (the application bundle is approximately 672 KB before gzip).

### Common messages

| Message / symptom | Meaning / action |
| --- | --- |
| Set product tax classification / HSN / unit | Finish product tax setup or the purchase-specific override |
| GSTIN checksum invalid | Re-enter the actual GSTIN; format acceptance is not a registration lookup |
| Header tax differs from line GST | Use calculated line tax; verify entered supplier invoice rates/basis |
| Date before GST effective date | Use the documented cutover; do not fabricate a tax snapshot for historical data |
| Period locked | Administrator must review and reopen the affected month with a reason |
| No GST snapshot | This is a legacy invoice; its existing thermal PDF remains available |
| Supplier invoice already recorded | Open the existing purchase; do not receive the same goods again |
| Unconfirmed manual note | Retry the frozen note to recover the original saved document |

## 15. Official reference material

The implementation uses the invoice particulars and separate tax-component concepts described in [CBIC invoice rules](https://cbic-gst.gov.in/gst-invoice-rules.html). That page includes older rule text; use current notifications and the accountant's applicable interpretation for the actual business.

The distinction between return preparation and filing, and the outward invoice/note/HSN data model, are informed by the [GST portal GSTR-1 guide](https://tutorial.gst.gov.in/userguide/returns/GSTR_1.htm). These application CSVs do not claim to match its upload schema.

Input review remains separate from automatically claimed credit, consistent with the conditions discussed in the [GST portal GSTR-2B advisory](https://tutorial.gst.gov.in/offlineutilities/returns/GSTR2B/GSTR-2B_Advisory.pdf). This release does not automate reconciliation or those conditions.
