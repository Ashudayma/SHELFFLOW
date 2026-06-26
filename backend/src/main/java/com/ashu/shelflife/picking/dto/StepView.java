package com.ashu.shelflife.picking.dto;

/**
 * The next location step the picker must scan (null when the order is fully picked).
 */
public record StepView(
        Long itemId,
        String sku,
        String itemName,
        String locationCode,
        int orderedQuantity,
        int pickedQuantity) {
}
