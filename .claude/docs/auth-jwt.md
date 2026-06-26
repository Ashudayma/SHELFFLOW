# Authentication & RBAC (JWT)

Implements BRD §3.1 "Secure Login" and the §2 RBAC table. Stateless JWT auth with
server-side refresh-token revocation.

## Roles
| Role          | Scope                                   | `warehouseIds` claim          |
|---------------|-----------------------------------------|-------------------------------|
| CENTRAL_ADMIN | Global / cross-warehouse                | empty (no restriction)        |
| HUB_PICKER    | Assigned warehouses only                | mapped ids from `picker_warehouse_mapping` at login |

Spring authority = `ROLE_<roleName>` (e.g. `ROLE_CENTRAL_ADMIN`), so `@PreAuthorize("hasRole('CENTRAL_ADMIN')")` works.

## Endpoints (`auth/AuthController`)
| Method & path     | Auth        | Body                       | Returns                          |
|-------------------|-------------|----------------------------|----------------------------------|
| POST /auth/login  | public      | `{usernameOrEmail, password}` | `{accessToken, refreshToken, tokenType, expiresIn}` |
| POST /auth/refresh| public      | `{refreshToken}`           | new token pair (refresh rotated) |
| POST /auth/logout | bearer token| `{refreshToken}`           | 204; revokes that refresh token  |

`usernameOrEmail` matches `users.email` first, then `users.name`.

## Tokens
- **Access token**: JWT, HMAC-SHA256, 15 min. Claims: `sub`=user id, `role`, `email`,
  `warehouseIds` (list; empty for CENTRAL_ADMIN). Issuer `shelflife`.
- **Refresh token**: opaque 256-bit random string (Base64URL). Only its **SHA-256 hash**
  is stored in `refresh_tokens` (table added by `V2__auth.sql`). 7-day lifetime.
  - **Rotation**: each `/auth/refresh` revokes the presented token and issues a new one.
  - **Logout**: marks the presented token revoked (scoped to the calling user; idempotent).

## RBAC enforcement convention
- `@EnableMethodSecurity` is on (`security/SecurityConfig`).
- **Every controller method from now on must carry an explicit `@PreAuthorize`.**
  - Public auth endpoints use `@PreAuthorize("permitAll()")`.
  - Admin-only example: `users/UserController#list` (`GET /users`) → `hasRole('CENTRAL_ADMIN')`.
  - Any-authenticated: `#me` / logout → `isAuthenticated()`.

## Error handling (structured JSON)
All auth/authorization errors return the same `ApiError` body
(`{timestamp, status, error, message, path}`):
- **401** — no/invalid/expired token (`security/RestAuthenticationEntryPoint`) or failed
  login / bad refresh token (`AuthenticationException` → `common/error/GlobalExceptionHandler`).
- **403** — authenticated but wrong role (`security/RestAccessDeniedHandler` for filter-level,
  `GlobalExceptionHandler` for `@PreAuthorize` denials raised during method invocation).

## Key classes
| Class | Role |
|-------|------|
| `security/JwtService` | issue/verify access tokens |
| `security/JwtAuthenticationFilter` | Bearer header → `SecurityContext` (`AuthenticatedUser` principal + `ROLE_` authority) |
| `security/AuthenticatedUser` | request-scoped principal (id, email, role, warehouseIds) |
| `security/SecurityConfig` | stateless filter chain, method security, BCrypt encoder |
| `security/JwtProperties` | binds `security.jwt.*` |
| `auth/AuthService` | login / refresh / logout, token hashing + rotation |
| `auth/RefreshToken` + repo | persisted refresh-token hashes |
| `common/error/*` | `ApiError` + `GlobalExceptionHandler` |

## Config (`application.yml` → `security.jwt`)
`secret` (Base64 HS256 key, override via `SECURITY_JWT_SECRET`), `access-token-expiration-minutes`,
`refresh-token-expiration-days`, `issuer`. The committed secret is **dev-only**.

## Tests
`backend/src/test/java/.../auth/AuthRbacIntegrationTest` (4 tests, `@Transactional` rollback over real PG):
1. HUB_PICKER token → `/users` (admin-only) ⇒ **403** + structured body.
2. CENTRAL_ADMIN token → `/users` ⇒ **200**.
3. No token → `/users` ⇒ **401** + structured body.
4. HUB_PICKER `/users/me` ⇒ role + mapped `warehouseIds` present (proves claim wiring).

Password hashing: BCrypt (`PasswordEncoder` bean).

## Not yet done / follow-ups
- No user-registration / admin user-management endpoints yet (users are created directly in tests).
- No scheduled cleanup of expired/revoked refresh tokens.
- `warehouseIds` claim is captured at login; it is **not** refreshed mid-session if a picker's
  mappings change — they re-propagate on next login/refresh.
- Consider per-warehouse data filtering in feature services using `AuthenticatedUser.warehouseIds()`.
