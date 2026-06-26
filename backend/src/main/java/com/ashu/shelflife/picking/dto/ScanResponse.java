package com.ashu.shelflife.picking.dto;

import com.ashu.shelflife.orders.OrderStatus;

/**
 * Result of a scan or skip: the affected item, the next step (or null if the order is now
 * complete), the order status, and whether this was an idempotent replay.
 */
public record ScanResponse(
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        ScannedItem item,
        StepView nextStep,
        boolean duplicate) {
}
