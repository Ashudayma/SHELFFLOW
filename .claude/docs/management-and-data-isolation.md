# User/Warehouse Management & Data Isolation

Implements admin account/warehouse management, Picker-to-Warehouse mapping (BRD 3.1), and
the BRD 3.1 **Data Isolation** rule.

## Path convention
Endpoints have **no `/api` prefix** (matching `/auth/*`): `/users`, `/warehouses`,
`/map-picker`, `/shelf-locations`. (The earlier sample `/api/users` was migrated to `/users`.)

## Admin endpoints (CENTRAL_ADMIN only)
| Method & path        | Purpose | Notes |
|----------------------|---------|-------|
| GET    /users        | list all accounts | |
| GET    /users/{id}   | get one | 404 if absent |
| POST   /users        | create HUB_PICKER / CENTRAL_ADMIN | 201; 409 on dup email; role must exist |
| PUT    /users/{id}   | full update | password optional (re-hash only if supplied); 409 on dup email |
| DELETE /users/{id}   | hard delete | removes picker mappings + refresh tokens first; **cannot delete self** (400) |
| GET    /warehouses   | list warehouses | |
| POST   /warehouses   | create warehouse | 201; 409 on dup `warehouse_code` |
| POST   /map-picker   | assign picker → warehouse(s) | body `{pickerId, warehouseIds[]}`; idempotent; only HUB_PICKER mappable (400 otherwise); 404 for missing picker/warehouse; returns full assigned set |

`GET /users/me` is available to any authenticated user.

Classes: `users/UserController` + `UserService` (+ DTOs), `warehouse/WarehouseController` +
`WarehouseService`, `warehouse/PickerMappingController` + `PickerWarehouseService`,
`warehouse/Warehouse` entity, `users/dto/*`, `warehouse/dto/*`.

## Data Isolation (the critical rule)
**A HUB_PICKER's accessible warehouse ids come only from their JWT `warehouseIds` claim —
never from a client-supplied request value.** Enforced at the **service layer**, centralized
in `warehouse/WarehouseScope`:

- `resolveQueryScope(principal, requestedWarehouseId)` → `Optional<List<Long>>`
  - CENTRAL_ADMIN, no filter → `empty()` = no restriction (sees all).
  - CENTRAL_ADMIN, filter → just that id.
  - HUB_PICKER, no filter → the claim set (silently scoped).
  - HUB_PICKER, filter **in** claims → that id.
  - HUB_PICKER, filter **outside** claims → throws `AccessDeniedException` (**403** — not honored).
- `assertAccessible(principal, warehouseId)` → guard for single-warehouse **mutations**
  (reusable; to be used by picking/order mutations as they land).

The principal is read from the `SecurityContext` (set by the JWT filter) via
`@AuthenticationPrincipal`, so the warehouse ids are always claim-derived, never request-derived.

### Representative picker-facing resource
`GET /shelf-locations?warehouseId=<optional>` (`inventory/ShelfLocationController` →
`ShelfLocationService`) is accessible to HUB_PICKER + CENTRAL_ADMIN and routes every read
through `WarehouseScope`. This is the concrete endpoint the isolation test targets; the same
pattern must wrap all future picker-facing warehouse-scoped queries/mutations (orders, picking).

## Errors added
`common/error`: `NotFoundException` (404), `ConflictException` (409). `GlobalExceptionHandler`
also maps `DataIntegrityViolationException` → 409 and `HttpMessageNotReadableException` → 400.

## Tests
- `inventory/PickerDataIsolationTest` (4) — **the BRD 3.1 proof**:
  1. picker, no filter ⇒ sees only assigned warehouse A (B never returned).
  2. picker, `?warehouseId=B` (foreign) ⇒ **403**, not honored.
  3. picker, `?warehouseId=A` (own) ⇒ honored.
  4. CENTRAL_ADMIN ⇒ sees both; `?warehouseId=B` honored — proving the 403 is per-claim
     isolation, not a blanket block.
- `users/AdminManagementTest` (3) — warehouse create, user create, map-picker, list, update;
  mapping a non-picker ⇒ 400; HUB_PICKER hitting `POST /users` ⇒ 403.

## Follow-ups
- `POST /map-picker` is additive/idempotent. If "replace all assignments" semantics are wanted, change `PickerWarehouseService.assign`.
- `warehouseIds` claim is fixed for an access token's life; remapping takes effect on next login/refresh.
- Hard delete only clears currently-populated dependents (mappings, refresh tokens). When
  picking/audit data references users, extend `UserService.delete` or switch to soft-delete.
- Apply `WarehouseScope.assertAccessible` to picker mutations once order/picking features exist.
