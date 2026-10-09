# Sales Profit

Open **Reports > Sales Profit** as an administrator or manager. The report defaults to today. Use an inclusive date range, Today / Last 7 days / Last 30 days, product name, barcode, exact category, and cost-coverage or loss-sale filters.

## What it shows

- Net sales excluding recorded GST, after item and final bill discounts.
- Cost of goods sold from recorded sale-time unit costs.
- **Estimated gross profit** and gross margin for the sales whose costs are known.
- Cost coverage by sale-line count, cost-covered revenue and quantities without recorded costs.
- Product totals and daily totals, with complete CSV export and Print / Save PDF for either breakdown.
- Highest total profit and highest margin rankings. Only products with complete cost coverage and positive profit qualify. Products with partial coverage still appear in the table with their known-cost contribution labelled.
- Loss-making sale-line counts and a filter for products containing at least one known-cost sale below cost, even when the product's overall profit is positive.

This is **gross profit**, not net business profit. Rent, wages, electricity, shrinkage and other expenses are not deducted. Standalone stock adjustments are not treated as sales or business expenses in this report.

## Costing basis and historical data

Every new invoice line captures the locked product's `costPrice` into immutable `bill_items.unit_cost_at_sale`. It is a cost per base stock unit, including sales scanned through alternate barcodes. Later product/purchase price changes do not rewrite that snapshot. Returns reverse the cost of returned units using the same snapshot. Idempotent bill retries reuse the original invoice and original snapshot.

Product cost reflects the latest received purchase, not weighted-average, FIFO, batch-specific or landed-cost accounting. Legacy purchases keep their entered unit price. Purchases posted after Regular GST setup set cost to the line's discounted ex-GST taxable value divided by its base quantity (six decimal places). This treats recorded GST separately even if it later proves ineligible; nonrecoverable tax and freight are not capitalized automatically. Keep manually entered costs on a consistent basis and review ineligible-tax costing with the accountant. See [GST accounting](gst-accounting.md).

Missing, negative or non-finite current costs are saved as unknown; an explicitly recorded zero cost is valid. Existing invoices remain unknown. There is deliberately no backfill from today's product prices, because that cannot recover historical cost. Enter correct product costs before further billing.

For mixed known/unknown costs:

```
reported profit = cost-covered net sales - known cost of goods sold
reported margin = reported profit / cost-covered net sales * 100
```

All net sales remain visible, including uncosted sales. Unknown sales do not contribute to either side of the profit calculation. With no cost-covered lines, profit and margin are unavailable. Margin is also unavailable for zero or negative cost-covered revenue. Cost coverage percentage measures line counts, not a revenue-weighted percentage; the covered revenue is displayed separately.

## Discounts, GST, returns and dates

- Item taxable amounts already contain item discounts.
- The current invoice's final instant discount is allocated across **all remaining lines before product or coverage filtering**, proportional to their taxable values. Paise use the largest-remainder method with stable item-ID ties, so allocations sum exactly to the final discount. If taxable values are all zero, gross values and then remaining quantities provide allocation weights.
- The full final discount reduces ex-GST sales. Recorded GST is shown separately and is not recalculated by this report. This follows the existing bill's `totalAmount - gstAmount` semantics. A very large final discount can produce negative ex-GST revenue; it is retained rather than hiding the loss.
- Partial returns use remaining quantities and prorated, rounded original line taxable/GST amounts. They retain the invoice's current final discount, following the existing return recalculation. Full returns and cancelled invoices are excluded.

The preceding discount rules apply to legacy invoices. **Invoices created after GST setup** already contain the allocated final discount in their stored taxable amounts and tax components. Profit uses these amounts without subtracting that discount again. The allocation happens when billing, across gross line values, before GST is extracted. Returns subtract rounded cumulative returned taxable/CGST/SGST/IGST amounts; the displayed remaining final discount is informational. Manual tax-only credit/debit notes do not change operational profit, stock or invoice balances. GST registers report dated notes separately and therefore need not match a profit report that restates the original sale date.
- Dates refer to the **original invoice date**. A later return changes that earlier day's profit. This is a view of currently retained sales, not an immutable ledger recording refunds on the return date. Days without matching sales are omitted from the daily table.
- Credit invoices count when billed. Later collections do not create additional sales or profit.
- Product ID groups renamed products together; the latest matching invoice line supplies the displayed name/barcode. Category is current product metadata. Barcode/name filters select matching sale snapshots.

## Backend and deployment

`GET /api/reports/sales-profit?from=2026-10-08&to=2026-10-08`

Optional parameters: `productName`, `barcode`, `category`, `view` (`ALL`, `COMPLETE`, `PARTIAL`, `MISSING`, `LOSS`). Coverage/loss filtering evaluates each matching product and recomputes summary/daily totals for the selected products. The read runs in one repeatable-read transaction with explicit tenant scope. Report access requires ADMIN or MANAGER; sale costs are not added to cashier invoice responses or customer receipts.

Restart the backend and update the frontend together. Development/desktop environments with Hibernate `ddl-auto=update` add the nullable cost column automatically. For deployments with `validate`/`none`, apply [the migration](migrations/20261008-sales-profit.sql) before starting the updated backend. It leaves existing invoices uncosted.

No executable rebuild is required during development. Rebuild the customer executable only when explicitly requested.
