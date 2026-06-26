package com.ashu.shelflife.picking.dto;

import java.util.List;

/**
 * Picker dashboard (BRD 3.3), all lists scoped to the active warehouse.
 *
 * @param available unclaimed PENDING orders in the active warehouse
 * @param current   this picker's ASSIGNED/PICKING orders
 * @param completed this picker's COMPLETED orders
 */
public record DashboardResponse(
        Long activeWarehouseId,
        List<OrderSummary> available,
        List<OrderSummary> current,
        List<OrderSummary> completed) {
}
