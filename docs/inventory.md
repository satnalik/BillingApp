# Inventory

Restart the backend after updating the source. The local, QA, and desktop profiles use Hibernate `ddl-auto=update`, which creates `stock_movements`. The production profile uses `validate`: apply [the PostgreSQL migration](migrations/20261008-stock-movements.sql) before starting the updated production backend. Open **Inventory** in the UI as an administrator or manager.

## Adjustments

Choose a product, then **Adjust stock**. Add units, remove units, or set the quantity found during a physical count. Select a reason: damaged, expired, lost, found, stock count, correction, or other. Other requires explanatory notes.

Every adjustment captures the logged-in account, date, reason, notes, and quantities before/change/after. A changed stock balance requires a refresh before retrying. An adjustment cannot result in negative stock. Product editing now retains the current stock; non-zero initial stock on a newly created product is recorded as opening stock.

## Movement history

Filter by product, movement type, or date. Sales, purchases, customer returns, and bill/purchase cancellations record stock movements in the same transaction as the original document. Their references open the associated bill. The API has no movement edit or delete endpoint.

Tracking applies to operations after this feature is installed. Existing non-zero stock receives a clearly labelled carried-forward balance on its first new movement. Earlier transactions are not reconstructed into invented movements. Products and history are scoped to the signed-in store.

## Opening stock import

1. Create the products with **zero** opening stock in Products.
2. In Inventory → Opening stock import, download the product template. It lists up to 1,000 zero-stock products.
3. Fill `quantity`, remove unused rows, and save as **CSV UTF-8**. `product_name` is informational. Use `product_id` or `barcode`; if both are provided they must identify the same product. Keep barcode columns as Text in Excel.
4. Upload the CSV (under 1 MB) and review the preview. Nothing changes during preview.
5. Import when every row is valid. The server rechecks every product under stock locks, then saves all rows together.

Quantities are base stock units, with at most two decimal places, even when a carton barcode identifies the product. Prices, purchase bills, and supplier balances are not created by this import. Notes are optional (up to 500 characters); the source file and a batch reference appear in history.

Opening stock is allowed only for a zero-stock product with no existing movement, sale, or purchase history. Duplicate products in one CSV and repeat imports are rejected. A zero opening quantity is also recorded, preventing repeated initialization of that product. Use a reasoned adjustment for later changes.

CSV columns:

```csv
product_id,product_name,barcode,quantity,notes
12,Example product,,25,Opening physical count
```

## API

All endpoints require an authenticated `ROLE_ADMIN` or `ROLE_MANAGER`. The tenant and actor come from the current session.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/inventory/adjustments` | `productId`, `mode` (`ADD`, `REMOVE`, `SET_COUNT`), `quantity`, `reason`, optional `notes`, `expectedStockQuantity` (required for counts) |
| GET | `/api/inventory/movements` | Optional `productId`, `type`, `from`, `to`, `page`, `size` |
| POST | `/api/inventory/opening-stock/preview` | `{ "sourceName": "opening.csv", "rows": [{ "productId": 12, "quantity": 25 }] }` |
| POST | `/api/inventory/opening-stock/import` | Same payload; revalidates and commits the complete batch |

The UI reads CSV into the JSON row payload. No spreadsheet library or executable rebuild is required for this source update.
