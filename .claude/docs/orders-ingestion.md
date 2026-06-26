# Order Ingestion (BRD 3.2)

Admin bulk-upload of orders from CSV or Excel.

## Endpoint
`POST /orders/upload` (CENTRAL_ADMIN only), `multipart/form-data`, form field **`file`**.
Returns a structured `OrderUploadReport` (HTTP 200) even when some rows fail.

- CSV (`.csv`) parsed with **OpenCSV**, Excel (`.xlsx`/`.xls`) with **Apache POI**.
  Format chosen by file extension (`orders/ingest/OrderFileParserFactory`).
- Numeric Excel cells are normalised (e.g. `5.0` → `"5"`).

## Required columns (exactly)
`Order_ID, Customer_ID, Warehouse_ID, Item_SKU, Item_Name, Quantity_Ordered`
Header match is case-insensitive + trimmed but must be exactly this set (missing/unexpected →
**400 file-level error**, before any row processing). See `orders/ingest/OrderUploadColumns`.

## Field mapping (confirmed interpretations)
- **Order_ID** → `orders.order_number` (unique). Repeated across rows = a multi-item order.
- **Customer_ID** → `orders.customer_id` — column **migrated to VARCHAR** in `V3__orders_customer_id_varchar.sql` to store string ids.
- **Warehouse_ID** → resolved via `warehouses.warehouse_code` (the business key), then stored as the numeric `orders.warehouse_id`.
- **Item_SKU** → `order_items.sku`; **Item_Name** → `order_items.item_name`; **Quantity_Ordered** → `order_items.ordered_quantity`.

## Per-row validation (each row independent)
1. All six fields present / non-blank.
2. `Quantity_Ordered` is a **positive integer**.
3. `Order_ID` not already in the DB (uniqueness).
4. `Warehouse_ID` exists (by `warehouse_code`).
5. `Item_SKU` has a `shelf_locations` row in that warehouse.

## Atomic orders + report (the key behaviour)
- Rows are grouped by `Order_ID`. An order is **atomic**: persisted only if **all** of its
  rows are valid and share one `Customer_ID` + `Warehouse_ID`.
- If any row of an order fails: that row → **FAILED** (specific error); valid siblings →
  **SKIPPED** ("Order '<id>' not ingested: row(s) N failed"). The order is not created.
- A fully-valid order → one `orders` row (status **PENDING**) + N `order_items`
  (status **PENDING**, `picked_quantity 0`); its rows → **SUCCESS**.
- The batch never fails wholesale: every data row appears in the report. File-level problems
  (bad header, unsupported type, unreadable) are the only 400s.

Report shape (`OrderUploadReport`): `{totalRows, successCount, failedCount, skippedCount,
ordersCreated, rows:[{row, orderId, status, error}]}`. `row` is the 1-based file line
(header = line 1).

## Key classes
`orders/OrderUploadController`, `OrderIngestionService` (validation + grouping + persistence),
`Order`/`OrderItem` entities (+ `OrderStatus`/`OrderItemStatus`, `@Version` on Order),
`OrderRepository`, `orders/ingest/*` (parsers, factory, columns, RawOrderRow),
`orders/dto/*` (report DTOs). Reuses `WarehouseRepository.findByWarehouseCode` and
`ShelfLocationRepository.existsByWarehouseIdAndSku`.

## Tests
`orders/OrderIngestionTest` (6): CSV grouping into orders/items; bad rows reported without
failing the batch (bad quantity / missing warehouse / no shelf location / duplicate id);
atomic order (one bad line → sibling SKIPPED, nothing persisted); Excel upload (POI);
invalid header → 400; HUB_PICKER upload → 403.

## Follow-ups
- Single DB transaction for the whole batch; since all rows are pre-validated, persistence
  won't fail mid-batch. If a concurrent insert created a duplicate `Order_ID` between
  validation and save, the unique constraint would roll the batch back (rare; could move to
  per-order `REQUIRES_NEW` if needed).
- Default multipart size limits apply (~1MB file / 10MB request) — raise via
  `spring.servlet.multipart.*` for large files.
- No de-dup of `Order_ID` across two different files beyond the DB uniqueness check.
