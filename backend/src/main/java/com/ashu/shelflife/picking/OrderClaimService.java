package com.ashu.shelflife.picking;

import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.orders.OrderStatus;
import com.ashu.shelflife.picking.dto.ClaimResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.warehouse.WarehouseScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.3 Order Selection: a picker claims a PENDING order, locking it from other pickers via
 * optimistic locking on {@code orders.version}.
 */
@Service
public class OrderClaimService {

    private final OrderRepository orderRepository;
    private final WarehouseScope warehouseScope;

    public OrderClaimService(OrderRepository orderRepository, WarehouseScope warehouseScope) {
        this.orderRepository = orderRepository;
        this.warehouseScope = warehouseScope;
    }

    /**
     * Claim an order for the picker. The update is conditioned on {@code status=PENDING AND
     * version=<expected>}; if it affects zero rows, another picker won the race and we return
     * a 409 conflict.
     *
     * @throws NotFoundException     if the order does not exist
     * @throws ConflictException     if the order is not PENDING / already claimed
     */
    @Transactional
    public ClaimResponse claim(Long orderId, AuthenticatedUser picker) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        // Data isolation: only orders in the picker's mapped warehouses may be claimed.
        warehouseScope.assertAccessible(picker, order.getWarehouseId());

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Order already claimed.");
        }

        int updated = orderRepository.claim(orderId, picker.id(), order.getVersion());
        if (updated == 0) {
            // Lost the race: another picker claimed it between our read and update.
            throw new ConflictException("Order already claimed.");
        }

        Order claimed = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        return ClaimResponse.from(claimed);
    }
}
