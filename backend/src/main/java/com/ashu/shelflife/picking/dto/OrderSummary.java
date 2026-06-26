package com.ashu.shelflife.picking.dto;

import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderStatus;

/**
 * Compact order view for the picker dashboard lists.
 */
public record OrderSummary(
        Long id,
        String orderNumber,
        String customerId,
        OrderStatus status,
        Long warehouseId,
        Long pickerId) {

    public static OrderSummary from(Order order) {
        return new OrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getStatus(),
                order.getWarehouseId(),
                order.getPickerId());
    }
}
