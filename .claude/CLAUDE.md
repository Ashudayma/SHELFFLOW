# ShelfLife — Project Context

> This file is auto-loaded by Claude Code as context. It is the source of truth for
> "what is this project and where is everything." Keep it current as the project grows.

## What this is

**ShelfLife** is a Spring Boot 3 / Java 21 backend for warehouse picking, inventory,
and order management. Built feature-by-feature on a Flyway-owned schema.

## Repository layout (monorepo)

```
ShelfLife/
  backend/   Spring Boot app (pom.xml, src/, .gitignore) — run mvn from here
  frontend/  Single Vite + React + MUI SPA: admin console (/admin/*) + picker UI (/picker/*)
  .claude/   Project docs / context
```
The backend was relocated from the repo root into `backend/`; all `mvn` commands run from
`backend/`. The admin and picker frontends were **consolidated into a single `frontend/` SPA**
(routes `/admin/*` and `/picker/*`, one shared Axios instance). The SPA reaches the backend via a
Vite `/api` dev proxy → `http://localhost:8080`.

**Implemented so far:**
- Project skeleton (V1 schema) + V2 `refresh_tokens`.
- **JWT authentication & RBAC** — login/logout/refresh, two roles, method-level
  `@PreAuthorize`, structured 401/403 errors. See `.claude/docs/auth-jwt.md`.
- **Admin management + Data Isolation** — `/users` CRUD, `/warehouses` (GET/POST),
  `/map-picker`, picker-facing `/shelf-locations`; service-layer warehouse scoping from JWT
  claims (`warehouse/WarehouseScope`). See `.claude/docs/management-and-data-isolation.md`.
- **Inventory master data** — `/products` (GET/POST, SKU management) and `/shelf-location`
  (POST/PUT, BRD 3.2 Master Location Mapping with regex-validated `location_code` and a
  service-layer 409 on the unique `(warehouse_id, sku)`). See
  `.claude/docs/inventory-master-data.md`.
- **Order ingestion** — `POST /orders/upload` (admin) accepts CSV (OpenCSV) + Excel (POI),
  validates per-row, returns a structured report, and groups successful rows into
  atomic orders (PENDING) + items. See `.claude/docs/orders-ingestion.md`.
- **Picker workflow (BRD 3.3 + 3.2 routing)** — `POST /picker/select-warehouse`,
  `GET /picker/dashboard` (scoped to active warehouse), `POST /orders/{id}/claim` (optimistic
  lock on `orders.version`, 409 on contention), `GET /orders/{id}/route` (items sorted by
  `location_code`), **Scan-to-Pick**: `POST /scan` (current-step enforcement, increment +
  pick_logs, completion + session close, optional `scanId` idempotency) and
  `POST /orders/{id}/items/{itemId}/skip`. See `.claude/docs/picker-workflow.md`.

- **Audit trail (BRD §4)** — cross-cutting: services publish `AuditEvent`s; an `@Async`
  `@TransactionalEventListener` writes `audit_logs` off the request thread (login, logout, scan,
  skip, item/order completion, report download). The scan path never blocks on it. See
  `.claude/docs/audit-trail.md`.
- **Dispatch report (BRD 3.4)** — `GET /report` (admin) with optional date/warehouse/picker
  filters; JSON + CSV (OpenCSV) + Excel (POI); dynamic `Fulfillment_Rate` (defensive on
  zero-ordered); logs a DOWNLOAD_REPORT audit event. See `.claude/docs/dispatch-report.md`.

**All BRD functional areas (3.1–3.4 + §4 audit) are now implemented.**

**Frontend** — `frontend/` (single Vite + React + TS + MUI v5 SPA; React Router, React Query, one
shared Axios instance with JWT interceptor + silent refresh). After login, a home page links to:
- **Admin console** (`/admin/*`, admin theme + drawer): Dashboard, Users (+ map-picker),
  Warehouses, Products, Shelf Mapping, Upload Orders (per-row report), Reports (filters + CSV/Excel).
- **Picker app** (`/picker/*`, high-contrast picker theme per BRD §4, applied via a nested
  ThemeProvider): Dashboard, Select Warehouse, Claim, Picking Screen (current-step emphasis,
  simulate-scan, skip, wrong-item feedback), Completed.

Dev calls go through a Vite `/api` proxy → :8080. `npm run build` green. **No role-based route
guarding yet** (`RequireAuth` is auth-only; both roles can reach both areas) — guards come next.
See `.claude/docs/frontend.md`.

Backend additions for the picker UI: `GET /picker/warehouses`; `RouteStep` carries `itemId`/`status`.

Remaining work is non-functional/polish (performance hardening, durability of the audit pipeline).

**Path convention:** endpoints have **no `/api` prefix** (e.g. `/auth/login`, `/users`,
`/warehouses`, `/map-picker`, `/shelf-locations`, `/products`, `/shelf-location`,
`/orders/upload`, `/picker/select-warehouse`, `/picker/dashboard`, `/orders/{id}/claim`,
`/orders/{id}/route`, `/scan`, `/orders/{id}/items/{itemId}/skip`, `/report`).
Note the deliberate pair: picker-facing read `GET /shelf-locations` (plural) vs admin write
`POST/PUT /shelf-location` (singular).

- **Group / package base:** `com.ashu.shelflife`
- **Artifact:** `shelflife` (version `0.0.1-SNAPSHOT`)
- **Build tool:** Maven (`backend/pom.xml`) — Gradle is not installed on this machine.
- **Java:** 21 (LTS)
- **Spring Boot:** 3.5.3
- **Architecture:** package-per-feature (see below)

## Package-per-feature layout

`backend/src/main/java/com/ashu/shelflife/`

| Package      | Responsibility                                              |
|--------------|-------------------------------------------------------------|
| `config`     | App-wide configuration (beans, properties, web/JPA setup)   |
| `security`   | Auth filters, password encoding, authorization rules        |
| `auth`       | Login, token issuance, session handling                     |
| `users`      | Users + roles management                                    |
| `warehouse`  | Warehouses + picker-warehouse mappings                      |
| `inventory`  | Products + shelf locations                                  |
| `orders`     | Orders + order items                                        |
| `picking`    | Pick sessions + pick logs                                   |
| `reports`    | Excel (Apache POI) + CSV (OpenCSV) exports                  |
| `audit`      | Audit log capture + querying                                |
| `common`     | Shared DTOs, exceptions, base entities, utilities           |

Each package currently holds only a `package-info.java` marker. `ShelflifeApplication.java`
is the `@SpringBootApplication` entry point at the package root.

## Database

- **Engine:** PostgreSQL **18.3**, `localhost:5432`, database `shelflife`, user `postgres`.
  - ⚠️ Note: the task brief expected "v15 on 5432", but on this machine **PG 18.3 listens
    on 5432** and the supplied credentials authenticate there. The PG 15 instance on 5433
    has different credentials. We targeted 5432 (PG 18) because that is what works.
- **Schema ownership:** `backend/src/main/resources/db/schema.sql`, applied on every startup by
  Spring's SQL init (`spring.sql.init.mode=always`, `spring.sql.init.schema-locations`). The
  script is **idempotent** — `CREATE TABLE/INDEX IF NOT EXISTS` and `INSERT … ON CONFLICT DO
  NOTHING` — so it creates + seeds a fresh DB and is a harmless no-op on a provisioned one.
  Hibernate stays `ddl-auto: validate` and must never alter tables.
- **Seed data:** roles, demo users (`admin@shelflife.com`/`Admin@123`,
  `picker@shelflife.com`/`Picker@123`), 2 warehouses, 5 products and their `WH-MAIN` shelf
  locations, and the picker↔warehouse mappings — all in the seed section of `schema.sql`.
- **Flyway is disabled** (`spring.flyway.enabled=false`). The historical migrations under
  `db/migration/` (`V1__init` … `V5__pick_logs_scan_id`) are retained for reference but no
  longer applied; `schema.sql` is the consolidated, current source of truth.
- **Local engine:** PostgreSQL on `localhost:5432`, database `shelflife` (override via the
  `SPRING_DATASOURCE_*` env vars). Under Docker Compose the DB is a `postgres:16-alpine`
  container reached by service name `postgres`.

See `.claude/docs/database-schema.md` for the full table breakdown.

## Build & run

**Full stack via Docker (preferred, zero local deps):** from the repo root,
`docker compose up --build` brings up postgres + backend + frontend; the app is pre-seeded.
SPA at http://localhost:3000, API at :8080, Swagger UI at http://localhost:8080/swagger-ui.html.
See the root `README.md` for the quickstart, credentials, and reset (`down -v`) details.

**Local Maven** (from `backend/`):

```powershell
cd backend
mvn clean compile        # compile + annotation processing (Lombok + MapStruct)
mvn test                 # full-context integration tests against a Postgres (uses SPRING_DATASOURCE_* if set)
mvn spring-boot:run      # run the app (port 8080); schema.sql runs on boot
```

The test suite needs a reachable PostgreSQL and manages its own data (it does not assert global
counts on a clean DB; point it at an empty database — e.g. a disposable container — to avoid
pollution from prior runs). See `.claude/docs/build-and-run.md` for prerequisites and DB setup.

## API docs

springdoc-openapi exposes Swagger UI at `/swagger-ui.html` and the spec at `/v3/api-docs`; both
are whitelisted in `SecurityConfig` (reachable without a token). `config/OpenApiConfig` registers
a JWT bearer scheme so the UI's **Authorize** button accepts an `accessToken` from `/auth/login`.

## Authentication & RBAC

JWT-based, stateless. See `.claude/docs/auth-jwt.md` for the full design. **Project rule
in force from now on: every controller method carries an explicit `@PreAuthorize`.**
CENTRAL_ADMIN = global scope; HUB_PICKER = assigned-warehouse scope (warehouse ids ride in
the JWT). Refresh tokens are stored hashed in `refresh_tokens` (migration V2) and rotated.

**Data Isolation (BRD 3.1) — also a standing rule:** every picker-facing warehouse-scoped
query/mutation MUST derive its `warehouse_id` filter from the JWT claims via
`warehouse/WarehouseScope`, never from a client-supplied parameter. A picker requesting a
warehouse outside their claims is rejected (403); an unfiltered query is scoped to their
warehouses. See `.claude/docs/management-and-data-isolation.md`.

## Key decisions

See `.claude/docs/decisions.md`. Highlights:
- Status columns are `VARCHAR + CHECK` (not native PG enums) for clean JPA `@Enumerated(STRING)` mapping.
- `products.sku` is a natural primary key (`VARCHAR`); other tables use `BIGINT IDENTITY`.
- All timestamps are `TIMESTAMPTZ`.
- MapStruct default component model is `spring` (set via compiler arg).

## What was done (work log)

See `.claude/docs/work-log.md` for the chronological record of the scaffolding session.
