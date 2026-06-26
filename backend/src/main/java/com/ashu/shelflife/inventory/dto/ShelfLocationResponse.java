package com.ashu.shelflife.inventory.dto;

import com.ashu.shelflife.inventory.ShelfLocation;

public record ShelfLocationResponse(
        Long id,
        Long warehouseId,
        String sku,
        String aisle,
        String bay,
        String shelf,
        String locationCode) {

    public static ShelfLocationResponse from(ShelfLocation location) {
        return new ShelfLocationResponse(
                location.getId(),
                location.getWarehouseId(),
                location.getSku(),
                location.getAisle(),
                location.getBay(),
                location.getShelf(),
                location.getLocationCode());
    }
}
