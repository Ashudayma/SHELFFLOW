package com.ashu.shelflife.warehouse.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Assign a HUB_PICKER to one or more warehouses (BRD 3.1 Picker-to-Warehouse Mapping).
 */
public record MapPickerRequest(
        @NotNull(message = "must not be null") Long pickerId,
        @NotEmpty(message = "at least one warehouse id is required") List<Long> warehouseIds) {
}
