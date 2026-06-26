# Design Decisions

Decisions made during initial scaffolding, with rationale. Update as the project evolves.

## Build tool: Maven
Gradle is not installed on this machine; Maven 3.9.9 is present and configured with JDK 21.
Chosen interactively with the user.

## Spring Boot 3.5.3 / Java 21
Latest stable Boot 3.x at scaffold time. Java 21 LTS as required.

## Target database: PostgreSQL 18.3 on port 5432
The brief expected "PG 15 on 5432", but on this machine:
- Port **5432** → PostgreSQL **18.3** (credentials `postgres` / `Ashu1234@` work here)
- Port **5433** → PostgreSQL 15 (different credentials — auth failed)

We targeted 5432 because that is where the supplied credentials authenticate. PG 18 is fully
compatible with Spring Boot 3 + Flyway. **If you intended the PG 15 instance, update
`spring.datasource` in `application.yml` and provide the 5433 password.**

## Status fields: VARCHAR + CHECK, not native PG enums
Easier to map with JPA `@Enumerated(EnumType.STRING)`, easier to extend (no `ALTER TYPE`),
and portable. Enum value sets are enforced at the DB via CHECK constraints.

## products.sku as natural primary key
The brief specifies `products(sku PK ...)`. SKU is the natural business key and is referenced
directly by `shelf_locations`, `order_items`, `pick_logs`. Other tables use surrogate
`BIGINT IDENTITY` keys.

## TIMESTAMPTZ everywhere
Timezone-aware timestamps avoid ambiguity. Defaults to `now()` where a creation/scan time applies.

## pick_logs / audit_logs keep denormalized sku/location
These are append-only history. They snapshot the sku/location string at event time and
intentionally avoid FKs so historical rows survive product/location changes.

## Flyway owns schema; Hibernate validates only
`spring.jpa.hibernate.ddl-auto: validate`. Hibernate must never create/alter tables — all
schema changes go through new `V__*.sql` migrations.

## MapStruct component model = spring
Set via compiler arg `-Amapstruct.defaultComponentModel=spring` so generated mappers are
Spring beans. Lombok + MapStruct cooperate via `lombok-mapstruct-binding` in the annotation
processor path.

## Authentication & RBAC (added in auth feature)
- **Stateless JWT.** Access token = signed JWT (HS256, 15 min); refresh token = opaque
  random string stored **hashed (SHA-256)** in `refresh_tokens`. Chosen over server-side
  sessions for scalability and over self-contained JWT refresh tokens so logout can truly
  revoke. See `auth-jwt.md`.
- **Refresh rotation** on every `/auth/refresh` (revoke old, issue new) — limits replay window.
- **`warehouseIds` in the access token** for HUB_PICKER, read from `picker_warehouse_mapping`
  at login. CENTRAL_ADMIN gets an empty list = global scope. Trade-off: a picker's scope is
  fixed for the token's 15-min life; mapping changes take effect on next login/refresh.
- **Authority naming:** `ROLE_<roleName>` so `hasRole('CENTRAL_ADMIN')` works against the
  seeded role names.
- **`@PreAuthorize` on every controller method** (project convention, enforced going forward),
  backed by `@EnableMethodSecurity`. Public endpoints use `permitAll()` explicitly.
- **Two error layers, one shape.** Filter-level denials use `AuthenticationEntryPoint` (401)
  / `AccessDeniedHandler` (403); exceptions during method invocation use
  `GlobalExceptionHandler`. Both emit the same `ApiError` JSON.
- **Dev JWT secret committed** in `application.yml`, overridable via `SECURITY_JWT_SECRET`.
  Acceptable for local dev only; real deployments must override.
- **timestamptz mapping:** entity timestamp columns use `OffsetDateTime`; Hibernate
  `validate` accepts these against `TIMESTAMPTZ` (verified — context starts cleanly).

## Order Ingestion (BRD 3.2)
- **Customer_ID → VARCHAR.** V3 migration widens `orders.customer_id` BIGINT→VARCHAR(64) to
  honor "Customer_ID is a string" (confirmed with user).
- **Warehouse_ID = warehouse_code.** File's Warehouse_ID is the business key, resolved to the
  numeric `orders.warehouse_id` (confirmed with user).
- **Atomic orders.** An order persists only if all its rows pass + share one Customer_ID /
  Warehouse_ID; otherwise bad rows → FAILED, valid siblings → SKIPPED, nothing persisted
  (confirmed with user). Avoids silently incomplete orders.
- **Per-row resilience, file-level strictness.** Row failures never fail the batch (200 +
  report); only header/format/unreadable problems are 400.
- **Format by extension** (`.csv`→OpenCSV, `.xlsx`/`.xls`→POI). Excel numeric cells normalised
  (`5.0`→`"5"`). Header must match the six required columns exactly (case-insensitive/trimmed).
- **`@Version` on Order** maps the existing `version` column for future optimistic-lock picking.

## Picker Workflow (BRD 3.3) + Routing (BRD 3.2)
- **Active warehouse persisted server-side** (`picker_active_warehouse`, migration V4) because
  auth is stateless JWT and the dashboard/claim carry no warehouse param.
- **`orders.picker_id`** added in V4 (the claim sets it).
- **Claim via conditional UPDATE** (`status=PENDING AND version=:expected`, increment version,
  set status/picker). Zero rows updated → 409. The DB row lock serializes concurrent claims so
  exactly one wins (verified by a 2-thread test). Chosen over entity `@Version` flush because it
  matches the BRD spec exactly and returns an affected-row count for a clean 409.
- **Claim scoped to mapped warehouses**, not coupled to the active-warehouse selection (keeps
  the claim path independent/simple). HUB_PICKER only; route readable by admin too.
- **Dashboard `completed`** = this picker's COMPLETED orders in the active warehouse.
- **Routing** sorts by `location_code` ascending; zero-padded `Aisle_<a>-Bay_<NN>-Shelf_<M>`
  codes sort lexicographically into the correct walk path. Unmapped-shelf items sort last.

## Scan-to-Pick (BRD 3.3)
- **Scan idempotency: implemented (optional).** Optional client `scanId` persisted as a unique
  `pick_logs.scan_id` (V5). Repeated scanId → idempotent no-op; the unique index prevents
  double-counting even under concurrent retries (the loser's insert fails and the scan rolls
  back). Keyless scans still work (NULLs distinct). Rationale: rugged scanners retry.
- **Scan transaction kept minimal** (validate → update item → insert pick_log → close session);
  the audit-trail write (§4) is deferred to the async mechanism (marked `TODO(async audit)` in
  `ScanService`), not done inline — protects scan latency.
- **Current step** = next PENDING item by `location_code`; when none, route returns to the first
  SKIPPED item (revisit), so a picker is never blocked. Wrong-SKU scan → 409.
- **Order completes when all items PICKED.** Accept-short completion is a documented enhancement
  (would mark leftover SKIPPED items short and feed the shortfall into the BRD 3.4 report).
- Scan/skip are HUB_PICKER-only and require the caller to own the claimed order.

## Audit Trail (BRD §4)
- **Cross-cutting via Spring events**, not AOP and not inline in controllers: services publish
  `AuditEvent`; `AuditEventListener` (`@Async("auditExecutor")` + `@TransactionalEventListener`
  AFTER_COMMIT) writes `audit_logs` in a REQUIRES_NEW tx. One publish line per action; the write
  logic lives in one place.
- **Off the request thread**: AFTER_COMMIT + @Async means the scan response never blocks on the
  audit write (proven by `AuditTrailAsyncTest`). Only committed actions are audited.
- **Recorded timestamp = action time** (captured when published), not the async write time.
- **No migration**: `audit_logs` already existed (V1). `order_id` stores the internal id.
- **Trade-off**: in-process async can drop events on JVM crash between commit and write; a
  transactional outbox/broker would harden durability (documented follow-up). Rejected scans
  aren't audited (they roll back before publish).

## Dispatch Report (BRD 3.4)
- **Dynamic JPQL** (filters appended only when present) instead of `:param is null` guards:
  PostgreSQL can't infer the type of a NULL bind parameter (`could not determine data type of
  parameter`). Theta join `OrderItem oi join oi.order o, Warehouse w where w.id = o.warehouseId`
  because `Order` has no `Warehouse` association.
- **`Fulfillment_Rate` computed in Java** (`ReportRow`), defensive on zero/null ordered → 0.0,
  rather than dividing in SQL (avoids divide-by-zero and keeps the edge case explicit).
- **Output/filter identifiers**: `Warehouse_ID` = warehouse_code (matches ingestion);
  `Picker_ID` = numeric picker id. JSON keys exactly the BRD names via `@JsonProperty`.
- **`date` = order creation date** (system-zone day boundaries). Completion-date filtering would
  use `pick_sessions.end_time` (enhancement).
- **All order lines included** (no status filter); PENDING lines show 0% — surfaces the full
  book. A COMPLETED/DISPATCHED-only mode is an easy add.
- **DOWNLOAD_REPORT audited on every access** (view + export) via the shared async mechanism.

## Open questions / things to revisit
- `pick_sessions.status` value set was not specified — using `OPEN/IN_PROGRESS/COMPLETED/CANCELLED` as a placeholder.
- `users.status` value set was not specified — using `ACTIVE/INACTIVE/SUSPENDED` as a placeholder.
- `orders.customer_id` / `order_items` have no `customers` table yet — `customer_id` is a bare BIGINT (no FK).
