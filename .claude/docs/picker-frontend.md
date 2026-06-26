# Picker UI (rugged-device) — now part of `frontend/`

> **Consolidated:** the picker UI was merged into the single `frontend/` SPA under routes
> `/picker/*` (see `.claude/docs/frontend.md`). The standalone `picker-frontend/` folder was
> removed. This doc retains the BRD §4 UX rationale and the picker-specific behaviour.

Picker pages live in `frontend/src/pages/` (`PickerDashboardPage`, `SelectWarehousePage`,
`ClaimOrderPage`, `PickingPage`, `CompletedOrdersPage`) under the `/picker/*` routes, wrapped by
`PickerLayout` (a nested MUI `ThemeProvider` applying `pickerTheme` + the `AppFrame` top bar).

## BRD §4 UI/UX (explicit) — `pickerTheme` in `src/theme.ts`
- **High contrast / WCAG AA**: text `#0a0a0a` on white ≈19:1; primary `#0b6b3a` and error
  `#b00020` ≥4.5:1 with white text.
- **Large fonts**: 16px base, 1.1–1.3rem controls, bold headings.
- **Minimal-touch / ≥44px targets**: buttons & inputs ≥56px, icon buttons ≥48px, list rows ≥64px;
  single-column `maxWidth=sm` (handheld portrait).

## Pages → routes → endpoints
| Page | Route | Backend |
|------|-------|---------|
| Select Warehouse | `/picker/select-warehouse` | `GET /picker/warehouses`, `POST /picker/select-warehouse` |
| Dashboard | `/picker` | `GET /picker/dashboard` (409 → redirect to Select Warehouse) |
| Claim Order | `/picker/orders/:id/claim` | `GET /orders/{id}/route` (preview) + `POST /orders/{id}/claim` |
| Picking Screen | `/picker/orders/:id/pick` | `GET /orders/{id}/route`, `POST /scan`, `POST /orders/{id}/items/{itemId}/skip` |
| Completed Orders | `/picker/completed` | `GET /picker/dashboard` (completed list) |

Login is the shared `/login` (no longer HUB_PICKER-only — role gating is deferred to the
route-guards step).

## Picking Screen (the core)
- **Current step** = first route step with `status === PENDING`, else first `SKIPPED` (mirrors the
  server rule). Shown in a big bordered/tinted card (location_code, item, picked/required qty) and
  marked `CURRENT` in the route list — the BRD location-by-location guidance.
- **Simulate Scan**: text input + Scan button (stands in for a hardware scanner) + "Scan current
  item" shortcut + Enter-to-scan. Wrong-item scans surface the backend's 409 message (expected SKU
  + location) in a prominent red alert.
- **Skip** → `/orders/{id}/items/{itemId}/skip`; route returns to skipped items later.
- After each scan/skip the route query is invalidated → state + current step recompute. Final pick →
  "Order complete" card.

## Auth
Uses the **single shared** Axios instance (`src/api/http.ts`) with the same bearer interceptor +
silent-refresh-on-401 as the admin pages (no separate client, unified token keys `shelflife.*`).

## Backend additions made for this UI (small, tested)
- `GET /picker/warehouses` (HUB_PICKER) → the picker's mapped warehouses (id/code/name); the admin
  `/warehouses` is admin-only so a picker otherwise had only raw ids.
- `RouteStep` gained `itemId` (so Skip can target the item) and `status` (PENDING/PICKED/SKIPPED, so
  the UI can mark the current step and render state). Covered by `PickerWorkflowTest`.
