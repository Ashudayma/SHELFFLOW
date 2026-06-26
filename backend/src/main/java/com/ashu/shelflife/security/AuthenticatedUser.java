package com.ashu.shelflife.security;

import java.util.List;

/**
 * Authenticated principal stored in the {@code SecurityContext} for the duration of a
 * request. Built from JWT claims by {@link JwtAuthenticationFilter}, so it is available
 * to controllers and SpEL expressions in {@code @PreAuthorize}.
 *
 * @param id           the user id
 * @param email        the user email
 * @param role         the role name (e.g. CENTRAL_ADMIN, HUB_PICKER)
 * @param warehouseIds warehouses in scope. Empty for CENTRAL_ADMIN (global scope);
 *                     the assigned warehouses for HUB_PICKER.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String role,
        List<Long> warehouseIds) {

    public boolean isCentralAdmin() {
        return "CENTRAL_ADMIN".equals(role);
    }
}
