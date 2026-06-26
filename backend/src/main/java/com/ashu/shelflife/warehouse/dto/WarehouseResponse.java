package com.ashu.shelflife.warehouse.dto;

import com.ashu.shelflife.warehouse.Warehouse;

public record WarehouseResponse(
        Long id,
        String warehouseCode,
        String warehouseName,
        String address) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getWarehouseCode(),
                warehouse.getWarehouseName(),
                warehouse.getAddress());
    }
}
