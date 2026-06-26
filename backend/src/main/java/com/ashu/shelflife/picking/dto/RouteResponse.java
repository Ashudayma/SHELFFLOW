package com.ashu.shelflife.picking.dto;

import java.util.List;

/**
 * The optimized travel path for an order (BRD 3.2 Routing Engine): line items sorted by
 * shelf {@code location_code} ascending.
 */
public record RouteResponse(
        Long orderId,
        String orderNumber,
        Long warehouseId,
        List<RouteStep> steps) {
}
