# ShelfLife Admin (frontend)

React + TypeScript + Vite + Material UI admin console for the ShelfLife backend.

## Stack
- React 18 + TypeScript, Vite
- Material UI (MUI v5)
- React Router v6 (navigation)
- TanStack React Query v5 (data fetching/caching)
- Axios with a JWT interceptor + silent-refresh-then-redirect on 401

## Run (dev)
```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```
The backend must be running on `http://localhost:8080`. The Vite dev server proxies
`/api/*` → `http://localhost:8080/*` (see `vite.config.ts`), so **no CORS config is needed in
dev** and frontend routes (e.g. `/users`) never collide with API paths.

Build / typecheck:
```bash
npm run build        # tsc + vite build
npm run typecheck
```

## Auth
- Login (`POST /auth/login`) stores the access + refresh tokens in `localStorage`.
- The Axios request interceptor attaches `Authorization: Bearer <access>`.
- On a `401`, the response interceptor performs **one** silent refresh
  (`POST /auth/refresh`), replays the original request, and on refresh failure clears tokens
  and redirects to `/login`. Concurrent 401s share a single in-flight refresh.
- The console is **Central Admin only**: after login the app verifies `role === CENTRAL_ADMIN`
  via `GET /users/me` and rejects others.

## Pages
| Route | Page | Backend |
|-------|------|---------|
| `/login` | Login | `/auth/login`, `/users/me` |
| `/` | Dashboard | counts from `/users`, `/warehouses`, `/products` |
| `/users` | Users CRUD + assign warehouses | `/users`, `/map-picker` |
| `/warehouses` | Warehouses | `/warehouses` |
| `/products` | Products | `/products` |
| `/shelf-mapping` | Shelf mapping (create/update) | `/shelf-location`, `/shelf-locations` |
| `/upload-orders` | Order upload + per-row report | `/orders/upload` |
| `/reports` | Dispatch report + CSV/Excel download | `/report` |

## Production note
For a separately-hosted frontend, either serve it behind a reverse proxy that maps `/api` to
the backend, or set `VITE_API_BASE_URL=http://your-backend` and enable CORS on the backend.
