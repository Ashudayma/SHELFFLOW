package com.ashu.shelflife.picking;

import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.inventory.ShelfLocation;
import com.ashu.shelflife.inventory.ShelfLocationRepository;
import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderItem;
import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.picking.dto.RouteResponse;
import com.ashu.shelflife.picking.dto.RouteStep;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.warehouse.WarehouseScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.2 Routing Engine: returns an order's line items sorted by their shelf
 * {@code location_code} to form the optimized travel path (e.g. Aisle_A-Bay_01 →
 * Aisle_A-Bay_02 → Aisle_B-Bay_01 → Aisle_C-Bay_02).
 *
 * <p>Our zero-padded code format ({@code Aisle_<a>-Bay_<NN>-Shelf_<M>}) sorts correctly with a
 * plain lexicographic comparison.
 */
@Service
public class RoutingService {

    private final OrderRepository orderRepository;
    private final ShelfLocationRepository shelfLocationRepository;
    private final WarehouseScope warehouseScope;

    public RoutingService(OrderRepository orderRepository,
                          ShelfLocationRepository shelfLocationRepository,
                          WarehouseScope warehouseScope) {
        this.orderRepository = orderRepository;
        this.shelfLocationRepository = shelfLocationRepository;
        this.warehouseScope = warehouseScope;
    }

    @Transactional(readOnly = true)
    public RouteResponse route(Long orderId, AuthenticatedUser caller) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        // Data isolation: a picker may only route orders in their mapped warehouses.
        warehouseScope.assertAccessible(caller, order.getWarehouseId());

        List<RouteStep> steps = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Optional<ShelfLocation> location =
                    shelfLocationRepository.findByWarehouseIdAndSku(order.getWarehouseId(), item.getSku());
            steps.add(new RouteStep(
                    0, // sequence assigned after sorting
                    item.getId(),
                    location.map(ShelfLocation::getLocationCode).orElse(null),
                    location.map(ShelfLocation::getAisle).orElse(null),
                    location.map(ShelfLocation::getBay).orElse(null),
                    location.map(ShelfLocation::getShelf).orElse(null),
                    item.getSku(),
                    item.getItemName(),
                    item.getOrderedQuantity(),
                    item.getPickedQuantity(),
                    item.getStatus()));
        }

        // Sort by location_code ascending (nulls — items without a mapped shelf — last).
        steps.sort(Comparator.comparing(RouteStep::locationCode,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<RouteStep> sequenced = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            RouteStep s = steps.get(i);
            sequenced.add(new RouteStep(i + 1, s.itemId(), s.locationCode(), s.aisle(), s.bay(),
                    s.shelf(), s.sku(), s.itemName(), s.orderedQuantity(), s.pickedQuantity(), s.status()));
        }

        return new RouteResponse(order.getId(), order.getOrderNumber(), order.getWarehouseId(), sequenced);
    }
}
