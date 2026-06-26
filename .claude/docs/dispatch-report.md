# Dispatch Report (BRD 3.4)

Admin performance report over order lines.

## Endpoint
`GET /report` (CENTRAL_ADMIN only). Optional query params:
| Param | Meaning |
|-------|---------|
| `date` | ISO date (`yyyy-MM-dd`) — filters by order **creation** date (system-zone day boundaries) |
| `warehouse` | Warehouse_ID = `warehouses.warehouse_code` |
| `picker` | Picker_ID = `orders.picker_id` (numeric) |
| `format` | `json` (default), `csv`, or `xlsx` |

Filters are independent and combine; omit to not filter. Unknown `format` → 400.

## Output columns (exactly, in this order)
`Order_ID, Picker_ID, Warehouse_ID, Item_SKU, Item_Name, Quantity_Ordered, Quantity_Picked, Fulfillment_Rate`
- One row per `order_items` line.
- `Order_ID` = `orders.order_number`; `Warehouse_ID` = warehouse code; `Picker_ID` = `orders.picker_id` (may be null/blank for unclaimed orders).
- **`Fulfillment_Rate` = Quantity_Picked / Quantity_Ordered**, computed dynamically per line in
  `ReportRow.fulfillmentRate`. **Defensive**: a zero/null `Quantity_Ordered` yields `0.0` (no
  division) — upload validation should prevent it, but the report won't blow up if present.
- JSON keys are exactly the BRD names (via `@JsonProperty` on the record); CSV/Excel headers match.

## Formats
- **JSON** (frontend table): array of rows.
- **CSV**: OpenCSV, `text/csv`, `Content-Disposition: attachment; filename="dispatch-report.csv"`. Rate formatted to 4 dp.
- **XLSX**: Apache POI, sheet "Dispatch Report", `attachment dispatch-report.xlsx`. Quantities and rate as numeric cells.

## Audit
Every `/report` access publishes a **DOWNLOAD_REPORT** `AuditEvent` (user id) via the shared
async mechanism (Prompt 8) — written to `audit_logs` off-thread, after the read-only tx commits.
Fires for all formats (view + export); move the publish into the csv/xlsx branches if only
exports should be audited.

## Key classes (`reports` package)
`ReportController` (`GET /report`), `ReportService` (query + CSV/Excel render + audit publish),
`DispatchReportRepository` (dynamic JPQL projection), `dto/ReportRow`.

## Notes / decisions
- **No status filter / no migration.** Report covers all order lines matching the filters
  (PENDING lines show 0% fulfillment). Reuses existing tables. A status filter (e.g. only
  COMPLETED/DISPATCHED) is an easy add if "dispatch" should mean dispatched-only.
- **Dynamic JPQL, not `:param is null` guards.** PostgreSQL cannot infer the type of a NULL bind
  parameter in `:param is null` ("could not determine data type of parameter"), so the query is
  assembled with only the supplied filters. The join is a theta join
  (`OrderItem oi join oi.order o, Warehouse w where w.id = o.warehouseId`) since `Order` has no
  `Warehouse` association.
- **`date` = order creation date** (system-zone day). Completion-date filtering would use
  `pick_sessions.end_time` — a possible enhancement.

## Tests
`reports/ReportTest` (8): exact fields + dynamic rate (1.0 / 0.25 / 0.0); zero-ordered → 0.0;
picker filter; date filter (today vs past); CSV header+rows; valid XLSX workbook; DOWNLOAD_REPORT
event published (`@RecordApplicationEvents`); HUB_PICKER → 403.
