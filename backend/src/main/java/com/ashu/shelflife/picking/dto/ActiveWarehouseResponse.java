package com.ashu.shelflife.picking.dto;

import java.time.OffsetDateTime;

public record ActiveWarehouseResponse(
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        OffsetDateTime selectedAt) {
}
