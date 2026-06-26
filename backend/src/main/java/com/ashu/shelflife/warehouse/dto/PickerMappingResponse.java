package com.ashu.shelflife.warehouse.dto;

import java.util.List;

/**
 * The picker's full set of assigned warehouses after a mapping operation.
 */
public record PickerMappingResponse(
        Long pickerId,
        List<Long> warehouseIds) {
}
