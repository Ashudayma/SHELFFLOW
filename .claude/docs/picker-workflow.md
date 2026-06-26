# Hub Picker Workflow (BRD 3.3 start + 3.2 Routing)

Picker-facing endpoints in the `picking` feature. HUB_PICKER unless noted.

## Endpoints
| Method & path | Role | Purpose |
|---------------|------|---------|
| GET /picker/warehouses | HUB_PICKER | The picker's mapped warehouses (id/code/name) for the Warehouse Entry screen (the admin `/warehouses` is admin-only). |
| POST /picker/select-warehouse | HUB_PICKER | Warehouse Entry: register active warehouse for the session. Body `{warehouseId}`; must be one of the picker's mapped warehouses else **403**. |
| GET /picker/dashboard | HUB_PICKER | Available (PENDING) + current (ASSIGNED/PICKING, this picker) + completed (COMPLETED, this picker) orders — all scoped to the **active** warehouse. **409** if no warehouse selected. |
| POST /orders/{id}/claim | HUB_PICKER | Order Selection: claim a PENDING order, locking it. **409** if already claimed; **403** if outside mapped warehouses; **404** if missing. |
| GET /orders/{id}/route | HUB_PICKER + CENTRAL_ADMIN | Routing Engine: order items sorted by shelf `location_code`. Picker limited to their warehouse scope (**403** otherwise). |

## Active warehouse (stateless session state)
Auth is stateless JWT, so the active warehouse is persisted per picker in
`picker_active_warehouse` (PK `picker_id`; migration V4). `select-warehouse` upserts it after
checking the warehouse is mapped (`WarehouseScope.assertAccessible`). `requireActiveWarehouseId`
re-validates the stored warehouse against current mappings (guards a revoked mapping).

## Claim — optimistic locking (the key mechanism)
`orders.picker_id` was added in V4. Claim is a single conditional UPDATE
(`OrderRepository.claim`, `@Modifying`):

```
update Order set status=ASSIGNED, picker_id=:pickerId, version = version + 1
where id=:id and status=PENDING and version=:expectedVersion
```

`OrderClaimService` loads the order (404/403/own-status pre-checks), then runs the update with
the order's current `version` as `:expectedVersion`. **Rows updated = 0 → 409** ("Order already
claimed"). The DB row lock serializes concurrent claims: the loser re-evaluates the WHERE
against the now-incremented version, matches nothing, and gets the 409. `clearAutomatically`
on the query keeps the persistence context fresh for the post-claim re-read.

## Routing Engine (BRD 3.2)
`RoutingService` joins each order item to its `shelf_locations` row (by `warehouse_id` + `sku`)
and sorts by `location_code` ascending. Each `RouteStep` also carries `itemId` and `status`
(PENDING/PICKED/SKIPPED) so a client can identify the current step (first PENDING, else first
SKIPPED), render state, and skip by item id. Our zero-padded format
`Aisle_<a>-Bay_<NN>-Shelf_<M>` sorts lexicographically into the correct walk path
(e.g. `Aisle_A-Bay_01` → `Aisle_A-Bay_02` → `Aisle_B-Bay_01` → `Aisle_C-Bay_02`). Items with
no mapped shelf sort last. Each step carries a 1-based `sequence`.

## Interpretations (chosen, documented)
- **Active warehouse stored server-side** (new table) rather than re-sent per request, since
  the BRD frames it as a session registration with no warehouse param on later calls.
- **Dashboard "completed"** = this picker's COMPLETED orders in the active warehouse (mirrors
  the picker-owned framing of "current"). Easy to widen to warehouse-wide if desired.
- **Claim** is scoped to the picker's *mapped* warehouses (data isolation), not coupled to the
  *active* warehouse selection — keeps claim independent and the concurrency path clean.
- **Claim is HUB_PICKER-only** (it sets `picker_id` = caller); **route** also allows admins.

## Tests
- `picking/OrderClaimConcurrencyTest` — two threads claim the same order simultaneously
  (CyclicBarrier); asserts exactly 1 success + 1 conflict, final status ASSIGNED / version 1 /
  picker set. Not `@Transactional` (threads commit); cleans up explicitly. Verified
  deterministic across repeated runs.
- `picking/PickerWorkflowTest` (8) — select warehouse + dashboard scoping; foreign warehouse
  select → 403; dashboard with no selection → 409; claim success + re-claim 409 + available→current
  move; foreign-order claim → 403; route sorted A1→A2→B1→C2; foreign-order route → 403; admin
  cannot select-warehouse → 403.

## Scan-to-Pick (BRD 3.3) — implemented

### Endpoints (HUB_PICKER; service enforces caller owns the order)
| Method & path | Purpose |
|---------------|---------|
| POST /scan | Scan `{orderId, sku, scanId?}` against the current location step. |
| POST /orders/{id}/items/{itemId}/skip | Mark an item SKIPPED and advance past it. |

### Current location step
The next **PENDING** item in route order (`location_code` ascending). When no PENDING items
remain, the route **returns to the first SKIPPED item** so it can be revisited — the picker is
never blocked. A scan whose SKU ≠ the current step's SKU is rejected with **409** and a message
naming the expected SKU/location (implements "prevent scanning items that don't belong to the
current step until completed or bypassed").

### On a correct scan (`ScanService`)
1. `picked_quantity += 1`; write one `pick_logs` row `(session_id, sku, quantity=1, location, scan_time)`.
2. If `picked_quantity == ordered_quantity` → item PICKED; else item PENDING (a revisited
   SKIPPED item becomes active again).
3. Pick session opened lazily on the first scan (`IN_PROGRESS`, `start_time`); order ASSIGNED→PICKING.
4. When **all items are PICKED** → order COMPLETED, session COMPLETED with `end_time`.

### Performance (BRD §4 "resolve instantly")
The scan transaction does only validate → update item → insert pick_log → (on completion) close
session. The **audit-trail write is off the scan path**: `ScanService` publishes `AuditEvent`s
and an `@Async` `@TransactionalEventListener` writes `audit_logs` after commit, on a separate
thread — never inline (see `.claude/docs/audit-trail.md`).

### Idempotency (decision: implemented, optional)
`scanId` is an optional client key. If a `scanId` was already recorded, the scan is an
idempotent no-op replay (`duplicate=true`, no second increment). Stored as a **unique** column
`pick_logs.scan_id` (migration V5; NULLs allowed/distinct so keyless scans still work). The
unique index is also the hard backstop: a duplicate that races past the pre-check fails the
insert and rolls the whole scan back, so double-counting is impossible. **Rationale:** rugged
scanners on flaky networks retry; without this a retry would double-count a pick.

### Tests
`picking/ScanToPickTest` (5): wrong-SKU rejection (409 with expected SKU); full pick →
increments, 3 pick_logs, order COMPLETED + session closed; skip → bypass then revisit →
completion; duplicate `scanId` → idempotent no-op (one log, no double count); scanning a
non-owned order → 403.

## Enhancement notes
- **Accept-short completion.** Today an order completes only when every item is PICKED; a
  permanently-unavailable SKIPPED item (e.g. out of stock) keeps it open. A natural enhancement
  is `POST /orders/{id}/complete` (admin or picker) that marks remaining SKIPPED items as
  short-accepted and completes the order, recording the shortfall for the dispatch report
  (BRD 3.4 `Quantity_Picked` < `Quantity_Ordered`). Not built yet — called out per the prompt.
- `OrderSummary` omits item count to avoid N+1; add via projection if the UI needs it.
