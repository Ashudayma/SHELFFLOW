package com.ashu.shelflife.picking;

import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.orders.OrderStatus;
import com.ashu.shelflife.picking.dto.DashboardResponse;
import com.ashu.shelflife.picking.dto.OrderSummary;
import com.ashu.shelflife.security.AuthenticatedUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.3 picker dashboard. Every list is scoped to the picker's active warehouse.
 */
@Service
public class PickerDashboardService {

    private static final List<OrderStatus> IN_PROGRESS =
            List.of(OrderStatus.ASSIGNED, OrderStatus.PICKING);

    private final ActiveWarehouseService activeWarehouseService;
    private final OrderRepository orderRepository;

    public PickerDashboardService(ActiveWarehouseService activeWarehouseService,
                                  OrderRepository orderRepository) {
        this.activeWarehouseService = activeWarehouseService;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AuthenticatedUser picker) {
        Long warehouseId = activeWarehouseService.requireActiveWarehouseId(picker);

        List<OrderSummary> available = orderRepository
                .findByWarehouseIdAndStatusOrderByCreatedAtAsc(warehouseId, OrderStatus.PENDING)
                .stream().map(OrderSummary::from).toList();

        List<OrderSummary> current = orderRepository
                .findByWarehouseIdAndPickerIdAndStatusInOrderByCreatedAtAsc(
                        warehouseId, picker.id(), IN_PROGRESS)
                .stream().map(OrderSummary::from).toList();

        List<OrderSummary> completed = orderRepository
                .findByWarehouseIdAndPickerIdAndStatusOrderByCreatedAtDesc(
                        warehouseId, picker.id(), OrderStatus.COMPLETED)
                .stream().map(OrderSummary::from).toList();

        return new DashboardResponse(warehouseId, available, current, completed);
    }
}
