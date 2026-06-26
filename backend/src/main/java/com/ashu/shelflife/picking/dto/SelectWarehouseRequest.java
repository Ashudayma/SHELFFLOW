package com.ashu.shelflife.picking.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Register the picker's active warehouse for the session. {@code warehouseId} must be one of
 * the picker's mapped warehouses.
 */
public record SelectWarehouseRequest(
        @NotNull(message = "must not be null") Long warehouseId) {
}
