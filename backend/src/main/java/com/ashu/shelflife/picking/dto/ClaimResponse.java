package com.ashu.shelflife.picking.dto;

import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderStatus;

public record ClaimResponse(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        Long pickerId,
        Long warehouseId,
        int version) {

    public static ClaimResponse from(Order order) {
        return new ClaimResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getPickerId(),
                order.getWarehouseId(),
                order.getVersion());
    }
}
