# Frontend (React + TS + MUI) — admin + picker in one SPA

Located in `frontend/`. Vite + React 18 + TypeScript + Material UI v5, React Router v6,
TanStack React Query v5, Axios. A **single** SPA serving the admin console (`/admin/*`) and the
picker app (`/picker/*`). (Consolidated from the former separate `frontend/` + `picker-frontend/`.)

## Run
```bash
cd frontend && npm install && npm run dev    # http://localhost:5173
```
Backend must run on :8080. `npm run build` (tsc + vite) verified green.

## Backend access (dev) — Vite proxy
Axios `baseURL = /api`; `vite.config.ts` proxies `/api/*` → `http://localhost:8080/*` (strips
`/api`). Avoids **CORS** in dev and the **route↔API-path collision**. Override with
`VITE_API_BASE_URL` for a direct/CORS setup.

## Single shared Axios instance + interceptor (`src/api/http.ts`)
One client for both areas (the prior two were merged):
- Tokens in `localStorage` (`tokenStore`, keys `shelflife.accessToken` / `shelflife.refreshToken`).
- Request interceptor attaches `Authorization: Bearer <access>`.
- Response interceptor: on **401** (non-auth call, not already retried) → one **silent refresh**
  via `POST /auth/refresh`, replay; concurrent 401s share one in-flight refresh; on failure → clear
  tokens + redirect to `/login`.
- Helpers: `apiErrorMessage`, `statusCode`.
- `AuthContext`: boot-time `/users/me` resolves the session. **`login` does NOT gate by role yet**
  (both CENTRAL_ADMIN and HUB_PICKER may sign in) — role-based route guarding comes in a later step.

## Routing
- `/login` — public.
- `RequireAuth` (auth-only outlet) wraps everything below:
  - `/` — `HomePage`: links to Admin Console and Picker App (+ logout).
  - `/admin/*` — `AdminLayout` (admin theme + drawer, `<Outlet/>`): index Dashboard, `users`,
    `warehouses`, `products`, `shelf-mapping`, `upload-orders`, `reports`.
  - `/picker/*` — `PickerLayout` (nested `ThemeProvider` with the high-contrast `pickerTheme` +
    `AppFrame`): index Dashboard, `select-warehouse`, `orders/:id/claim`, `orders/:id/pick`,
    `completed`.

## Structure
```
src/
  api/http.ts        single axios instance + interceptors + tokenStore + apiErrorMessage + statusCode
  api/api.ts         all endpoints: auth, users, warehouses, products, mapping, shelf, report,
                     orders (upload+claim+route), picker, scan
  auth/AuthContext.tsx
  components/        RequireAuth, AdminLayout (drawer), PickerLayout (picker theme + AppFrame),
                     AppFrame, PageHeader
  pages/            Login, Home; admin: Dashboard, Users, Warehouses, Products, ShelfMapping,
                    UploadOrders, Reports; picker: PickerDashboard, SelectWarehouse, ClaimOrder,
                    Picking, CompletedOrders
  types.ts          DTO mirrors (admin + picker)
  theme.ts          `theme` (admin) + `pickerTheme` (rugged-device)
  App.tsx           routes; main.tsx providers (root = admin theme)
```

## Pages → endpoints
**Admin** (`/admin/*`): Dashboard (counts from `/users`,`/warehouses`,`/products`); Users (CRUD
`/users` + `/map-picker`); Warehouses (`/warehouses`); Products (`/products`); Shelf Mapping
(`/shelf-locations`, `/shelf-location`); Upload Orders (`/orders/upload`, per-row report); Reports
(`/report` JSON + CSV/Excel blob download).
**Picker** (`/picker/*`): Select Warehouse (`/picker/warehouses`, `/picker/select-warehouse`);
Dashboard (`/picker/dashboard`, 409 → select-warehouse); Claim (`/orders/{id}/route` + `/orders/{id}/claim`);
Picking (`/orders/{id}/route`, `/scan`, `/orders/{id}/items/{itemId}/skip`; current-step card +
route highlight + wrong-item alert); Completed (`/picker/dashboard`).

## Notes
- Picker rugged-device UX (BRD §4) preserved via `pickerTheme` (high contrast/WCAG AA, ≥44px touch
  targets), scoped to the `/picker` subtree by a nested `ThemeProvider`.
- Tables are plain MUI `Table` (no `@mui/x-data-grid`).
- One package.json (`shelflife-frontend`); the two source apps had identical dep versions — no
  conflicts. Build-verified; not wired into the Maven build (ships/deploys separately).
