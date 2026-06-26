package com.ashu.shelflife.warehouse;

import com.ashu.shelflife.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Central enforcement point for the BRD 3.1 "Data Isolation" rule.
 *
 * <p>The warehouse ids a HUB_PICKER may touch come <strong>only</strong> from their JWT
 * claims ({@link AuthenticatedUser#warehouseIds()}), never from a client-supplied request
 * value. A client-supplied {@code warehouseId} is treated as a <em>filter request</em> that
 * is validated/intersected against that claim set — it can never widen access.
 *
 * <p>CENTRAL_ADMIN has global scope and is exempt.
 *
 * <p>Service-layer code must route every picker-facing warehouse-scoped query through
 * {@link #resolveQueryScope} and every single-warehouse mutation through
 * {@link #assertAccessible}.
 */
@Component
public class WarehouseScope {

    /**
     * Resolve the set of warehouse ids a query may read.
     *
     * @param principal           the authenticated caller (from the security context, not the request body)
     * @param requestedWarehouseId an optional client-supplied warehouse filter; may be {@code null}
     * @return {@code Optional.empty()} meaning "no restriction" (CENTRAL_ADMIN, no filter);
     *         otherwise the concrete list of warehouse ids the query must be limited to.
     * @throws AccessDeniedException if a picker requests a warehouse outside their assigned scope
     */
    public Optional<List<Long>> resolveQueryScope(AuthenticatedUser principal, Long requestedWarehouseId) {
        if (principal.isCentralAdmin()) {
            return requestedWarehouseId == null
                    ? Optional.empty()
                    : Optional.of(List.of(requestedWarehouseId));
        }

        List<Long> assigned = principal.warehouseIds();
        if (requestedWarehouseId == null) {
            // No filter supplied: scope silently to the picker's assigned warehouses.
            return Optional.of(List.copyOf(assigned));
        }
        if (!assigned.contains(requestedWarehouseId)) {
            // Foreign warehouse id is never honored.
            throw new AccessDeniedException(
                    "Warehouse " + requestedWarehouseId + " is outside your assigned scope.");
        }
        return Optional.of(List.of(requestedWarehouseId));
    }

    /**
     * Assert that the caller may act on a single warehouse. Use this to guard picker-facing
     * mutations before they touch warehouse-scoped rows.
     *
     * @throws AccessDeniedException if a picker targets a warehouse outside their assigned scope
     */
    public void assertAccessible(AuthenticatedUser principal, Long warehouseId) {
        if (principal.isCentralAdmin()) {
            return;
        }
        if (warehouseId == null || !principal.warehouseIds().contains(warehouseId)) {
            throw new AccessDeniedException(
                    "Warehouse " + warehouseId + " is outside your assigned scope.");
        }
    }
}
