package com.ashu.shelflife.picking.dto;

import com.ashu.shelflife.orders.OrderItemStatus;

/**
 * One stop on the optimized pick path: a line item and the shelf to walk to. {@code status}
 * lets a client mark the current step (next PENDING in sequence) and show PICKED/SKIPPED state.
 */
public record RouteStep(
        int sequence,
        Long itemId,
        String locationCode,
        String aisle,
        String bay,
        String shelf,
        String sku,
        String itemName,
        Integer orderedQuantity,
        Integer pickedQuantity,
        OrderItemStatus status) {
}
