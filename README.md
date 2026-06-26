# ShelfLife

Outbound warehouse **picking & dispatch** system. Monorepo with a Spring Boot 3 / Java 21
backend and a single React SPA serving both the admin console (`/admin/*`) and the picker
app (`/picker/*`).

```
backend/            Spring Boot 3 / Java 21 API (PostgreSQL). Build with Maven.
frontend/           React + TS + MUI SPA — admin console (/admin) + picker UI (/picker).
docker-compose.yml  Full stack: postgres + backend + frontend.
```

---

## Quickstart — `docker compose up`

**Prerequisites:** Docker Desktop (or Docker Engine) with the Compose plugin. Nothing else —
no local JDK, Maven, Node, or Postgres required.

From a clean clone:

```bash
docker compose up --build
```

That single command:
1. starts **PostgreSQL** with a named volume and waits until it is healthy,
2. builds and starts the **backend**, which on boot runs `backend/src/main/resources/db/schema.sql`
   to create the schema and seed demo data (idempotently), then waits until it is healthy,
3. builds and starts the **frontend** (nginx), which reverse-proxies `/api/*` to the backend.

When it settles you have a fully working, **pre-seeded** app with zero manual steps.

| What                | URL                                            |
|---------------------|------------------------------------------------|
| Web app (SPA)       | http://localhost:3000                          |
| API (direct)        | http://localhost:8080                          |
| **Swagger UI**      | http://localhost:8080/swagger-ui.html          |
| OpenAPI spec (JSON) | http://localhost:8080/v3/api-docs              |

### Seeded demo credentials

| Role           | Email                  | Password     | Scope                          |
|----------------|------------------------|--------------|---------------------------------|
| Central Admin  | `admin@shelflife.com`  | `Admin@123`  | Global (all warehouses)        |
| Hub Picker     | `picker@shelflife.com` | `Picker@123` | Warehouses `WH-MAIN`, `WH-NORTH` |

Also seeded: 2 warehouses, 5 products, and their shelf locations in `WH-MAIN` — enough master
data to upload orders (Admin → Upload Orders) and run the picker flow end-to-end without any
manual setup.

> **Using Swagger UI with auth:** call `POST /auth/login` with one of the credentials above,
> copy the `accessToken`, click **Authorize**, and paste it. The protected endpoints are then
> callable from the UI.

### Trying it end-to-end

A sample order file (`dummy_orders_upload.csv`) is included for convenience — it uses the
seeded `WH-MAIN` warehouse code and the 5 seeded SKUs, so it uploads cleanly against a fresh
clone with no edits needed:

1. Log in as **Admin** → **Upload Orders** → upload `dummy_orders_upload.csv`.
2. Log in as **Picker** → select **WH-MAIN** → claim an order → follow the generated route →
   simulate scans to complete picking.
3. Back in **Admin** → **Reports** → view the dispatch report and fulfillment rate.

### Tear down / reset

```bash
docker compose down       # stop & remove containers (keeps the database volume)
docker compose down -v    # also drop the database volume — the next `up` re-seeds from scratch
```

Because `schema.sql` is idempotent (`CREATE TABLE/INDEX IF NOT EXISTS`, `INSERT … ON CONFLICT
DO NOTHING`), a `docker compose down -v && docker compose up` re-creates and re-seeds cleanly
with no "relation already exists" or duplicate-key/constraint errors.

---

## Architecture

**Backend — package-per-feature.** A stateless, JWT-secured Spring Boot API organised by
business capability rather than by technical layer. Each feature package owns its controllers,
services, entities and DTOs: **`auth`** (login / token issuance / refresh-token rotation),
**`users`** (user + role CRUD), **`warehouse`** (warehouses and picker↔warehouse mappings),
**`inventory`** (products + master shelf-location mapping), **`orders`** (CSV/Excel order
ingestion and order/item state), **`picking`** (claim with optimistic locking, the
location-ordered routing engine, and scan-to-pick), **`reports`** (CSV/Excel dispatch report
with fulfillment-rate calculation), and **`audit`** (an async, cross-cutting audit trail).
Supporting packages `security`, `config`, and `common` hold the JWT filter chain, app
configuration (incl. OpenAPI), and shared DTOs/exceptions. Roles are `CENTRAL_ADMIN` (global)
and `HUB_PICKER` (restricted to assigned warehouses, enforced from JWT claims so a picker can
never reach another warehouse's data — including by passing a foreign order ID directly).
The schema is created and demo data seeded on startup by `db/schema.sql` via Spring's SQL
init; Hibernate runs in `validate` mode and never alters the schema. The **frontend** is a
single Vite + React + MUI SPA; in Docker it is served by nginx, which strips the `/api` prefix
and proxies to the `backend` service over the Docker network.

### Notable design decisions

- **Optimistic locking on order claims** — `orders.version` ensures two pickers cannot claim
  the same order; a losing concurrent claim returns `409 Conflict` rather than silently
  overwriting state.
- **Service-layer warehouse isolation** — a picker's accessible warehouse IDs come from their
  JWT claims, never from a client-supplied parameter, so a foreign warehouse's order can't be
  read or acted on even if its ID is guessed or passed directly.
- **Async audit trail** — login, logout, scan, skip, and order-completion events are logged via
  an async listener so audit writes never add latency to the scan-to-pick path.
- **Location-sorted routing** — when a picker claims an order, items are returned sorted by
  shelf `location_code`, producing an optimized walk path rather than upload order.

---

## Local development (without Docker)

Requires JDK 21, Maven, Node 18+, and a local PostgreSQL with a `shelflife` database. The
backend's `application.yml` defaults to `jdbc:postgresql://localhost:5432/shelflife`; override
with the `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
environment variables as needed.

**Backend** — API on http://localhost:8080:
```bash
cd backend
mvn spring-boot:run
```

**Frontend** — http://localhost:5173 (Vite dev server proxies `/api` → `:8080`):
```bash
cd frontend
npm install && npm run dev
```

### Tests

The backend test suite is full-context integration tests against a real PostgreSQL. Point it at
any empty database (the suite manages its own data):

```bash
cd backend
# against a disposable throwaway database, e.g.:
#   docker run -d --name sl-test -e POSTGRES_DB=shelflife -e POSTGRES_USER=postgres \
#     -e POSTGRES_PASSWORD=postgres -p 5544:5432 postgres:16-alpine
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5544/shelflife \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
mvn test
```

Coverage includes the order-claim race condition (optimistic lock → one wins, one 409),
scan-sequence validation, the dispatch fulfillment-rate calculation (incl. the zero-ordered
edge case), and warehouse data isolation (incl. direct foreign order-ID access on
claim / scan / route).

---

## Possible future enhancements

A few ideas beyond the core requirements, noted here for context rather than implemented:

- **Idempotent scan handling** — a client-generated idempotency key on `/scan` to guard against
  duplicate increments if a rugged handheld device retries a request after a flaky connection.
- **Partial/short-pick status** — an explicit `SHORT_PICKED` order status with a reason code for
  out-of-stock scenarios, rather than leaving an order open indefinitely.
- **Route re-optimization after a skip** — regenerate the remaining route when an item is
  skipped, instead of only appending it to the end of the original route.
- **Stale-claim auto-release** — a background job that returns an `ASSIGNED` order to `PENDING`
  if a picker goes inactive past a timeout, so a dropped device doesn't block an order forever.
- **Live admin dashboard updates** — push picker progress to the admin console via WebSocket
  instead of polling.