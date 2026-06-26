package com.ashu.shelflife.picking.dto;

import com.ashu.shelflife.orders.OrderItem;
import com.ashu.shelflife.orders.OrderItemStatus;

/**
 * State of the line item affected by a scan or skip.
 */
public record ScannedItem(
        Long itemId,
        String sku,
        String itemName,
        int orderedQuantity,
        int pickedQuantity,
        OrderItemStatus status) {

    public static ScannedItem from(OrderItem item) {
        return new ScannedItem(
                item.getId(),
                item.getSku(),
                item.getItemName(),
                item.getOrderedQuantity(),
                item.getPickedQuantity(),
                item.getStatus());
    }
}
