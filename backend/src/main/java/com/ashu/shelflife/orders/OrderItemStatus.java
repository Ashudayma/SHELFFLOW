package com.ashu.shelflife.orders;

/**
 * Order-item pick state. Mirrors the CHECK constraint on order_items.status.
 */
public enum OrderItemStatus {
    PENDING,
    PICKED,
    SKIPPED
}
