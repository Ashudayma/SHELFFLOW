# Work Log

> Note: as of the 2026-06-26 monorepo restructure, the backend lives in `backend/` — run all
> `mvn` commands from there. Historical entries below predate the move and reference root-level
> `src/`, `pom.xml`, and `mvn` (then at the repo root).

## 2026-06-26 — Frontend consolidation (admin + picker → one SPA)

Goal: merge `picker-frontend/` into `frontend/` as one project; routes `/admin/*` (existing admin)
and `/picker/*` (migrated picker); single shared Axios instance; no role guards yet.

### package.json
The two apps had **identical** dependency + devDependency versions — **no conflicts**. Kept one
`package.json`, renamed `shelflife-admin` → `shelflife-frontend`.

### Single API client
Merged into `frontend/src/api/`: one `http.ts` (added `statusCode`; unified token keys
`shelflife.*` — one session) and one `api.ts` (added `pickerApi`, `scanApi`; extended `ordersApi`
with `claim`/`route`). Deleted the picker's duplicate `http.ts`/`api.ts`.

### Routing & layouts
- `RequireAuth` (auth-only outlet) replaces the two `ProtectedRoute`s. `AuthContext.login` no
  longer rejects by role (both roles may sign in) — guards deferred.
- `AdminLayout` (drawer, links → `/admin/*`, renders `<Outlet/>`), `PickerLayout` (nested
  `ThemeProvider(pickerTheme)` + `AppFrame` + `<Outlet/>`). New `HomePage` at `/` links to both.
- `theme.ts` now exports `theme` (admin) + `pickerTheme` (picker). Picker theme scoped to `/picker`.
- Migrated 5 picker pages into `frontend/src/pages/` (picker `DashboardPage` → `PickerDashboardPage`
  to avoid the collision); rewrote all in-page `navigate(...)`/redirect paths to `/picker/*`, and
  admin Dashboard stat-card links to `/admin/*`. Merged picker types into `types.ts`.
- Removed `AppLayout.tsx`, `ProtectedRoute.tsx`.

### Verification
- `npm run build` (tsc strict + vite) → **green** (1069 modules). Dev server booted and served
  `http://localhost:5173/` (HTTP 200, SPA shell) before removing the old folder.
- Deleted `picker-frontend/` after verification.

### Docs
Updated root `README.md`, `CLAUDE.md` (layout + frontend section), `frontend.md` (rewritten for the
unified app), `picker-frontend.md` (consolidation banner; routes now `/picker/*`).

### Status: ✅ One frontend project; admin `/admin/*` + picker `/picker/*` compile & run. Role guards next.

---

## 2026-06-26 — Monorepo restructure (backend → backend/)

Goal: move the Spring Boot app into `backend/` so the repo cleanly holds three modules
(`backend/`, `frontend/`, `picker-frontend/`). No application logic changed — reorg only.

### Moves
- `src/` → `backend/src/`, `pom.xml` → `backend/pom.xml`, root `.gitignore` (the Maven one) →
  `backend/.gitignore`.
- Deleted the stale root `target/` (transient build output; regenerates as `backend/target/`).
- New repo-root `.gitignore` covering both ecosystems.

### Notes
- No Maven wrapper / `.vscode` / root README / CI workflow existed. The only `.github` content is
  the pre-existing `modernize/java-upgrade/` tool (references its own path only — left untouched).
- Frontends call the backend via the Vite `/api` proxy → `http://localhost:8080` (runtime, no
  path change).

### Docs updated
`CLAUDE.md` (repo layout, `backend/` build commands, package/migration paths), `build-and-run.md`
(run from `backend/`), `database-schema.md` + `auth-jwt.md` (path prefixes). Added root `README.md`.

### Verification
- `cd backend && mvn clean test` → **BUILD SUCCESS**, 48 tests, 0 failures. Both frontends already
  build green and are unaffected.

### Status: ✅ Restructure complete; backend builds/runs from `backend/`.

---

## 2026-06-25 — Initial scaffolding

Goal: create the ShelfLife Spring Boot 3 / Java 21 skeleton (schema + structure, no business logic).

### Environment checks
- Confirmed JDK 21.0.9, Maven 3.9.9, psql 15.14.
- Found two Postgres services running: `postgresql-x64-15` and `postgresql-x64-18`.
- Discovered port **5432 → PG 18.3** (creds work), port **5433 → PG 15** (creds fail).
  Targeted 5432. `shelflife` database already existed.

### Decisions taken with user
- Build tool: **Maven** (Gradle not installed).
- DB: **5432**, db `shelflife`, user `postgres`, password `Ashu1234@`.

### Files created
- `pom.xml` — Spring Boot 3.5.3, Java 21. Dependencies: Web, Security, Data JPA, Validation,
  PostgreSQL driver, Flyway (core + database-postgresql), Lombok, MapStruct, Apache POI
  (poi + poi-ooxml), OpenCSV. Compiler plugin wired with Lombok + MapStruct + lombok-mapstruct-binding
  annotation processors; MapStruct component model = spring.
- `src/main/java/com/ashu/shelflife/ShelflifeApplication.java` — `@SpringBootApplication` entry point.
- `package-info.java` in each feature package: config, security, auth, users, warehouse,
  inventory, orders, picking, reports, audit, common.
- `src/main/resources/application.yml` — datasource, JPA (`ddl-auto: validate`, `open-in-view: false`),
  Flyway (`baseline-on-migrate`), server port 8080.
- `src/main/resources/db/migration/V1__init.sql` — all 11 tables + role seed (CENTRAL_ADMIN, HUB_PICKER).
- `src/test/java/com/ashu/shelflife/ShelflifeApplicationTests.java` — contextLoads smoke test.
- `.gitignore` — standard Java/Maven/IDE ignores.
- `.claude/` — this documentation set (CLAUDE.md + docs/).

### Verification
- `mvn clean compile` → success (annotation processing OK).
- `mvn test` → BUILD SUCCESS, 1 test passed.
  - Flyway: "Successfully applied 1 migration to schema public, now at version v1".
- DB inspection: 12 relations (11 domain tables + flyway_schema_history), `roles` seeded
  (CENTRAL_ADMIN=1, HUB_PICKER=2), flyway_schema_history version 1 / init / success=t.

### Status: ✅ Skeleton complete and building cleanly. Migration runs against Postgres.

### Next steps (not yet done — no business logic by design)
- JPA entities per feature, mapped to the V1 tables.
- Spring Security config (currently default).
- DTOs + MapStruct mappers, repositories, services, controllers.
- Report exporters (POI / OpenCSV) in `reports`.
- A `customers` table if `orders.customer_id` should be a real FK.

---

## 2026-06-25 — JWT authentication & RBAC

Goal: implement BRD §3.1 Secure Login + §2 RBAC (CENTRAL_ADMIN / HUB_PICKER). Stateless JWT,
login/logout/refresh, method-level `@PreAuthorize`, structured 401/403, RBAC integration test.
(No BRD file in repo — built against the requirements stated in the request.)

### Added
- `pom.xml`: jjwt 0.12.6 (api/impl/jackson).
- `V2__auth.sql`: `refresh_tokens` table (hashed token, expiry, revoked flag).
- Entities/repos: `users` (Role, User, UserStatus, repos), `warehouse`
  (PickerWarehouseMapping + composite id + repo), `auth` (RefreshToken + repo).
- Security: `JwtProperties`, `JwtService`, `JwtAuthenticationFilter`, `AuthenticatedUser`,
  `RestAuthenticationEntryPoint` (401), `RestAccessDeniedHandler` (403), `SecurityConfig`
  (`@EnableWebSecurity` + `@EnableMethodSecurity`, stateless, BCrypt).
- Auth: `AuthService` (login/refresh/logout, SHA-256 hashing, rotation), `AuthController`
  (`/auth/login`, `/auth/refresh`, `/auth/logout`), DTOs.
- Sample RBAC controller: `users/UserController` — `GET /api/users` (CENTRAL_ADMIN only),
  `GET /api/users/me` (any authenticated).
- Errors: `common/error/ApiError` + `GlobalExceptionHandler`.
- `application.yml`: `security.jwt.*` block.
- Test: `auth/AuthRbacIntegrationTest` (4 tests, `@Transactional`).

### Decisions
- Refresh tokens opaque + stored hashed (real revocation); rotated on refresh.
- `warehouseIds` claim populated for HUB_PICKER from `picker_warehouse_mapping` at login.
- Authority = `ROLE_<role>`; every controller method gets explicit `@PreAuthorize`.
- timestamptz columns mapped as `OffsetDateTime` — `validate` passes.

### Verification
- `mvn clean compile` → success.
- `mvn test` → **BUILD SUCCESS**, Tests run: 5, Failures: 0, Errors: 0.
  - Flyway applied V2 ("now at version v2").
  - RBAC: HUB_PICKER→403, CENTRAL_ADMIN→200, no-token→401, picker token carries warehouseIds.

### Status: ✅ JWT auth + RBAC complete and verified.

---

## 2026-06-25 — Admin management + Data Isolation (BRD 3.1)

Goal: admin user/warehouse CRUD, picker-to-warehouse mapping, and service-layer data
isolation for pickers (warehouse scope from JWT claims, never client params) + proof test.

### Path change
Standardized all endpoints to **no `/api` prefix** (match `/auth/*`). Migrated the earlier
`/api/users`(+`/me`) → `/users`(+`/me`); updated `AuthRbacIntegrationTest` accordingly.

### Added
- **Entities/repos:** `warehouse/Warehouse` (+repo), `inventory/ShelfLocation` (+repo).
  Repo additions: `UserRepository.existsByEmail(+AndIdNot)`, `RefreshTokenRepository.deleteByUserId`,
  `PickerWarehouseMappingRepository.deleteByPickerId`.
- **Users:** `UserService` + DTOs (`Create/UpdateUserRequest`, `UserResponse`); rewrote
  `UserController` → full CRUD at `/users` + `/users/me`. Delete clears mappings + refresh
  tokens, blocks self-delete.
- **Warehouses:** `WarehouseService` + `WarehouseController` (`GET/POST /warehouses`) + DTOs.
- **Mapping:** `PickerWarehouseService` + `PickerMappingController` (`POST /map-picker`),
  idempotent, HUB_PICKER-only.
- **Data isolation:** `warehouse/WarehouseScope` (`resolveQueryScope` + `assertAccessible`).
  Picker-facing `inventory/ShelfLocationController` + `ShelfLocationService` route reads
  through it (`GET /shelf-locations`).
- **Errors:** `common/error/NotFoundException` (404), `ConflictException` (409); handler now
  also maps `DataIntegrityViolationException`→409, `HttpMessageNotReadableException`→400.

### Decisions
- Warehouse scope derived from JWT claims via a single service-layer guard (`WarehouseScope`);
  controllers pass `@AuthenticationPrincipal` (security-context, not request body).
- Foreign warehouse id from a picker → **403 (rejected)**; unfiltered picker query → scoped
  to claims. Admin exempt (global).
- `map-picker` additive/idempotent; only HUB_PICKER mappable.
- Manual DTO mapping (no MapStruct yet) to keep the diff focused.

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **12**, Failures 0, Errors 0.
  - `PickerDataIsolationTest` (4): no-filter scoped to assigned WH; foreign id ⇒ 403; own id
    honored; admin sees all + can filter any WH.
  - `AdminManagementTest` (3): warehouse/user create + map-picker + list/update; non-picker
    mapping ⇒ 400; picker `POST /users` ⇒ 403.
  - `AuthRbacIntegrationTest` (4, updated paths) + contextLoads (1) still green.

### Status: ✅ Admin management + BRD 3.1 Data Isolation complete and verified.

---

## 2026-06-25 — Inventory master data: Products + Master Location Mapping (BRD 3.2)

Goal: admin SKU management and SKU→location_code mapping with service-layer uniqueness (409)
and regex format validation (400). All in the `inventory` feature.

### Added
- **Products:** `inventory/Product` (entity, assigned String PK), `ProductRepository`,
  `ProductService` (create with 409 on dup sku, list), `ProductController` (`GET/POST /products`),
  `dto/CreateProductRequest` + `ProductResponse`.
- **Location mapping:** `LocationCodes` (regex validate/parse) + `ParsedLocation`;
  `dto/ShelfLocationRequest`; extended `ShelfLocationRepository`
  (`findByWarehouseIdAndSku`, `existsByWarehouseIdAndSku`); extended `ShelfLocationService`
  with `create`/`update`; `ShelfLocationAdminController` (`POST/PUT /shelf-location`).

### Decisions
- Client sends `{warehouseId, sku, locationCode}`; `aisle/bay/shelf` are **parsed** from the
  validated code (not client-supplied) so stored parts always match the code.
- Regex `^Aisle_([A-Za-z0-9]+)-Bay_([0-9]+)-Shelf_([0-9]+)$` (e.g. `Aisle_A-Bay_04-Shelf_2`);
  malformed → 400 with descriptive message.
- Unique `(warehouse_id, sku)` enforced via `existsByWarehouseIdAndSku` → clear 409
  (`ConflictException`); DB constraint + DataIntegrity→409 handler remain a race backstop.
- Unknown warehouse/sku → 404 before insert (no raw FK errors).
- POST `/shelf-location` creates; PUT updates existing (warehouse, sku) (404 if absent).
- Path: admin write `/shelf-location` (singular, per BRD wording); picker read
  `/shelf-locations` (plural). Two paths, deliberate.

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **18**, Failures 0, Errors 0.
  - `ProductAndLocationMappingTest` (6): product create/list + dup 409; map + dup 409;
    malformed code 400; missing warehouse/sku 404; update + 404 on unmapped; picker 403.
  - All prior tests still green.

### Status: ✅ Inventory master data (products + BRD 3.2 location mapping) complete and verified.

---

## 2026-06-25 — Order Ingestion (BRD 3.2): CSV + Excel upload

Goal: `POST /orders/upload` (admin) accepting CSV (OpenCSV) and Excel (POI), per-row
validation with a structured report, successful rows grouped into orders + items.

### Confirmed interpretations (asked the user)
- `Warehouse_ID` = `warehouses.warehouse_code` (business key) → resolved to numeric id.
- `Customer_ID` is a string → **V3 migration** alters `orders.customer_id` BIGINT→VARCHAR(64).
- Orders are **atomic**: if any row of an Order_ID fails, the order isn't created (bad rows
  FAILED, valid siblings SKIPPED).

### Added
- Migration `V3__orders_customer_id_varchar.sql`.
- Entities/repos: `orders/Order` (+ `@Version`), `OrderItem`, `OrderStatus`, `OrderItemStatus`,
  `OrderRepository` (`existsByOrderNumber`, `findByOrderNumber`). `WarehouseRepository.findByWarehouseCode`.
- Parsing: `orders/ingest/` — `OrderFileParser` + `CsvOrderFileParser` (OpenCSV),
  `ExcelOrderFileParser` (POI, numeric-cell normalisation), `OrderFileParserFactory`,
  `OrderUploadColumns` (exact-header validation), `RawOrderRow`.
- Service: `OrderIngestionService` (per-row validate w/ caches, group by Order_ID,
  atomic persistence, build report). Controller: `OrderUploadController` (multipart, admin).
- DTOs: `OrderUploadReport`, `OrderUploadRowResult`, `RowResultStatus` (SUCCESS/FAILED/SKIPPED).

### Decisions
- File format by extension; bad header/unsupported/unreadable → 400 (file-level), distinct
  from per-row failures (always 200 with report).
- Per-row validations: required fields, positive-int quantity, Order_ID uniqueness (DB),
  warehouse exists (by code), SKU has shelf_location in that warehouse.
- Intra-order consistency: one Customer_ID + Warehouse_ID per Order_ID, else the order fails.
- Whole batch in one tx; rows pre-validated so persistence won't fail mid-batch.

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **24**, Failures 0, Errors 0.
  - Flyway applied V3 ("now at version v3").
  - `OrderIngestionTest` (6): CSV grouping (2 orders / 3 items, picked_qty 0); bad rows
    reported w/o failing batch (quantity/warehouse/shelf/duplicate); atomic order (sibling
    SKIPPED, nothing persisted); Excel upload; bad header 400; picker 403.

### Status: ✅ Order ingestion (BRD 3.2) complete and verified.

---

## 2026-06-25 — Picker workflow start (BRD 3.3) + Routing (BRD 3.2)

Goal: Warehouse Entry, picker dashboard (scoped to active warehouse), order claim with
optimistic locking + concurrency test, and the routing engine. (BRD at `.claude/docs/BRD.md`.)

### Added
- Migration `V4__picker_workflow.sql`: `orders.picker_id` (FK→users) + `picker_active_warehouse` table.
- `Order.pickerId` field; `OrderRepository` dashboard queries + `@Modifying claim(...)` conditional update.
- `picking/` feature: `PickerActiveWarehouse` (+repo), `ActiveWarehouseService`,
  `PickerDashboardService`, `OrderClaimService`, `RoutingService`, `PickerController`
  (`/picker/select-warehouse`, `/picker/dashboard`), `OrderPickingController`
  (`/orders/{id}/claim`, `/orders/{id}/route`), DTOs.

### Decisions (documented in picker-workflow.md)
- Active warehouse stored server-side (`picker_active_warehouse`) — stateless JWT, no warehouse
  param on dashboard/claim.
- Dashboard `completed` = this picker's COMPLETED orders in the active warehouse.
- Claim = conditional UPDATE `status=PENDING AND version=:expected` → 0 rows → 409; scoped to
  mapped warehouses (not coupled to active warehouse). HUB_PICKER only; route also allows admin.
- Routing: sort items by `location_code` ascending (zero-padded codes sort into the walk path).

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **33**, Failures 0, Errors 0. Flyway V4 applied.
  - `OrderClaimConcurrencyTest`: two simultaneous claims → exactly 1 success + 1 conflict,
    final ASSIGNED/version 1/picker set. Ran 3× — deterministic (DB row-lock serialization).
  - `PickerWorkflowTest` (8): select/dashboard scoping, foreign select 403, no-selection 409,
    claim success + re-claim 409 + available→current, foreign claim 403, route sorted
    A1→A2→B1→C2, foreign route 403, admin select 403.

### Status: ✅ Picker workflow start (BRD 3.3) + routing (BRD 3.2) complete and verified.

---

## 2026-06-26 — Scan-to-Pick (BRD 3.3)

Goal: `POST /scan` with current-location-step enforcement, increment + pick_logs, completion +
session close; `POST /orders/{id}/items/{itemId}/skip` with revisit; performance-minimal scan
transaction (audit deferred to async).

### Added
- Migration `V5__pick_logs_scan_id.sql` (optional unique `pick_logs.scan_id` idempotency key).
- Entities/repos: `PickSession` (+`PickSessionStatus`, repo), `PickLog` (+repo,
  `findByScanId`). `ShelfLocationRepository.findByWarehouseIdAndSkuIn`.
- `picking/ScanService` (current step = next PENDING in route order, else first SKIPPED;
  wrong-SKU → 409; increment + pick_log; PICKED/PENDING transition; all-PICKED → order
  COMPLETED + session closed; idempotent replay on repeated scanId). `ScanController`
  (`POST /scan`, `POST /orders/{id}/items/{itemId}/skip`), DTOs.

### Decisions
- **Idempotency implemented** via optional `scanId` (unique column); replay = no-op, unique
  index is the double-count backstop. Rationale: rugged scanners retry on flaky networks.
- **Scan transaction minimal** (validate/update/log/close); audit-trail write deferred to the
  async mechanism with a marked `TODO(async audit)` hook — not inline (BRD §4 latency).
- **Skip = SKIPPED + revisit**: route returns to skipped items once PENDING is exhausted.
- **Completion = all items PICKED.** "Accept-short" completion documented as an enhancement.
- Scan/skip are HUB_PICKER-only and require the caller to own the claimed order (403 otherwise).
- Wrong-SKU and lifecycle violations → 409 ConflictException.

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **38**, Failures 0, Errors 0. Flyway V5 applied.
  - `ScanToPickTest` (5): wrong-SKU 409; full pick → 3 pick_logs + COMPLETED + session closed;
    skip → bypass + revisit → completion; duplicate scanId idempotent (1 log); non-owner 403.
  - Note: one test initially failed because `JdbcTemplate` doesn't autoflush Hibernate dirty
    updates; fixed with an explicit `flush()` before raw-SQL assertions (production code was correct).

### Status: ✅ Scan-to-Pick (BRD 3.3) complete and verified.

---

## 2026-06-26 — Audit Trail (BRD §4) as a cross-cutting concern

Goal: audit login/logout/scan/skip/pick-&-order-completion via Spring events + an @Async
listener writing audit_logs, with the scan response not blocking on the audit write.

### Added (audit package + wiring)
- `AuditEvent` (record, BRD fields + action), `AuditAction` enum, `AuditLog` entity +
  `AuditLogRepository`, `AuditLogWriter` seam + `JpaAuditLogWriter` (REQUIRES_NEW),
  `AuditEventListener` (@Async("auditExecutor") @TransactionalEventListener AFTER_COMMIT).
- `config/AsyncConfig` (@EnableAsync + dedicated audit executor).
- Publishers: `AuthService` (LOGIN/LOGOUT), `ScanService` (SCAN/ITEM_PICKED/ORDER_COMPLETED,
  SKIP) — replaced the prior `TODO(async audit)` hook with real event publishing.
- No migration: `audit_logs` already exists from V1.

### Decisions
- **Spring events over AOP**: one publish line per action in the domain layer; the write is
  centralized in the listener (cross-cutting, not scattered across controllers).
- **AFTER_COMMIT + @Async + REQUIRES_NEW**: only committed actions audited; write fully off the
  request thread and independent. Rejected scans (throw → rollback) aren't audited (documented).
- Timestamp recorded is the action time (captured in the event), not the async write time.

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **39**, Failures 0, Errors 0.
  - `AuditTrailAsyncTest`: scan returns fast (< 5s) while the audit write is gated (20s);
    no audit row committed at response time; after releasing the gate the row appears async
    with exactly the BRD fields. Non-`@Transactional` (scan must commit) + explicit cleanup.
  - Debug note: initial latch test failed due to a CGLIB-proxy artifact (@Transactional on the
    test writer → Objenesis-instantiated proxy with uninitialized latch fields). Fixed by making
    the test writer non-transactional (repo.save provides its own committing tx).

### Status: ✅ Audit trail (BRD §4) complete and verified.

---

## 2026-06-26 — Dispatch Report (BRD 3.4)

Goal: `GET /report` (admin) with optional date/warehouse/picker filters; JSON + CSV/Excel;
dynamic fulfillment rate (defensive zero-ordered); DOWNLOAD_REPORT audit event.

### Added (reports package)
- `dto/ReportRow` (JSON keys = exact BRD names via @JsonProperty; 7-arg ctor computes rate,
  defensive on zero/null ordered).
- `DispatchReportRepository` (dynamic JPQL projection joining order_items→orders→warehouses;
  theta join for Warehouse; filters appended only when present).
- `ReportService` (generate + audit publish; `toCsv` via OpenCSV; `toExcel` via POI).
- `ReportController` (`GET /report`, format=json|csv|xlsx, admin-only).
- `AuditAction.DOWNLOAD_REPORT`.
- No migration (reuses existing tables).

### Decisions
- Dynamic JPQL instead of `:param is null` guards — Postgres can't infer the type of a NULL
  bind param ("could not determine data type of parameter"). (Hit and fixed during testing.)
- `Warehouse_ID` output/filter = warehouse_code; `Picker_ID` = numeric picker id.
- `date` filter = order creation date (system-zone day). No status filter (all lines included).
- DOWNLOAD_REPORT audited on every /report access (all formats).

### Verification
- `mvn clean test` → **BUILD SUCCESS**, Tests run: **47**, Failures 0, Errors 0.
  - `ReportTest` (8): exact fields + rates (1.0/0.25/0.0); zero-ordered → 0.0; picker filter;
    date filter (today vs past); CSV header+rows; valid XLSX; DOWNLOAD_REPORT event published
    (@RecordApplicationEvents); HUB_PICKER → 403.

### Status: ✅ Dispatch Report (BRD 3.4) complete. All BRD functional requirements (3.1–3.4 + §4) done.

---

## 2026-06-26 — Admin frontend (React + TS + MUI)

Goal: admin SPA — Login, Dashboard, Users, Warehouses, Products, Shelf Mapping, Upload Orders
(per-row report), Reports (filters + CSV/Excel). React Router + React Query + Axios JWT
interceptor with silent refresh.

### Added (`frontend/`)
- Vite + React 18 + TS + MUI v5 project (package.json, tsconfig(s), vite.config.ts with `/api`
  dev proxy → :8080, index.html, env, README).
- `api/http.ts` (axios instance, tokenStore, request interceptor attaching JWT, response
  interceptor: 401 → single shared silent refresh → replay, else clear+redirect),
  `api/api.ts` (typed endpoints), `auth/AuthContext.tsx` (admin-only guard).
- `components/` AppLayout (AppBar+Drawer), ProtectedRoute, PageHeader.
- `pages/` Login, Dashboard (counts), Users (CRUD + map-picker dialog), Warehouses, Products,
  ShelfMapping (create/update + per-warehouse list), UploadOrders (summary chips + per-row
  status/error table), Reports (date/warehouse/picker filters, % fulfillment, CSV/Excel blob
  download). `types.ts`, `theme.ts`, `App.tsx`, `main.tsx`.

### Decisions
- `/api` Vite proxy → avoids CORS in dev and the frontend-route vs API-path collision.
- Admin-only console: post-login `/users/me` role check rejects non-CENTRAL_ADMIN.
- Dashboard counts derived from list endpoints (no stats endpoint); plain MUI tables (no x-data-grid).
- map-picker dialog is additive (no GET-current-mappings endpoint) — shows resulting set.

### Verification
- `npm install` + `npm run build` (tsc -b + vite build) → **green** (1045 modules; strict TS incl.
  noUnusedLocals/Parameters). Advisory only: single ~550 kB MUI chunk.

### Status: ✅ Admin frontend complete and build-verified.

---

## 2026-06-26 — Picker frontend (rugged-device UI, BRD §4)

Goal: Hub Picker SPA — Login, Select Warehouse, Dashboard, Claim, Picking Screen (current-step
emphasis, simulate-scan, skip, wrong-item feedback), Completed. High contrast/large fonts/≥44px
touch targets.

### Backend additions (small, tested)
- `GET /picker/warehouses` (HUB_PICKER) → mapped warehouses with code/name
  (`ActiveWarehouseService.mappedWarehouses` + `PickerController`).
- `RouteStep` gained `itemId` and `status`; `RoutingService` populates them. New assertions in
  `PickerWorkflowTest` (warehouses endpoint + step status). Full suite: **48 tests green**.

### Added (`picker-frontend/`)
- Vite + React + TS + MUI project (port 5174, `/api` proxy). High-contrast theme with ≥44px
  touch targets in `theme.ts`.
- `api/http.ts` (JWT interceptor + silent refresh, namespaced token keys), `api/api.ts`,
  `auth/AuthContext.tsx` (HUB_PICKER guard), `components/` AppFrame + ProtectedRoute.
- Pages: Login → Select Warehouse → Dashboard (409 → select) → ClaimOrder (route preview + claim)
  → Picking (route + scan + skip; current-step card + route list highlight; wrong-item alert;
  completion state) → Completed Orders.

### Verification
- `npm install` + `npm run build` (tsc + vite) → **green** (1034 modules). Backend `mvn test` → 48 green.

### Status: ✅ Picker frontend complete and build-verified. (Admin + picker frontends both done.)
