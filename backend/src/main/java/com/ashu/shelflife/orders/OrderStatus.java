package com.ashu.shelflife.orders;

/**
 * Order lifecycle state. Mirrors the CHECK constraint on orders.status.
 */
public enum OrderStatus {
    PENDING,
    ASSIGNED,
    PICKING,
    COMPLETED,
    DISPATCHED
}
