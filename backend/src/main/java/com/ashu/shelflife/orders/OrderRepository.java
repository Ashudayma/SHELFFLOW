package com.ashu.shelflife.orders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNumber(String orderNumber);

    Optional<Order> findByOrderNumber(String orderNumber);

    // --- Picker dashboard (BRD 3.3), all scoped to a single warehouse -------------------

    /** Available queue: unclaimed orders in a warehouse, oldest first. */
    List<Order> findByWarehouseIdAndStatusOrderByCreatedAtAsc(Long warehouseId, OrderStatus status);

    /** A picker's in-progress orders (ASSIGNED/PICKING) in a warehouse. */
    List<Order> findByWarehouseIdAndPickerIdAndStatusInOrderByCreatedAtAsc(
            Long warehouseId, Long pickerId, Collection<OrderStatus> statuses);

    /** A picker's completed orders in a warehouse, most recent first. */
    List<Order> findByWarehouseIdAndPickerIdAndStatusOrderByCreatedAtDesc(
            Long warehouseId, Long pickerId, OrderStatus status);

    /**
     * Atomically claim an order (BRD 3.3 Order Selection): conditioned on the order still
     * being PENDING at the expected optimistic-lock version. Increments the version, sets
     * status=ASSIGNED and picker_id. Returns the number of rows updated — 0 means another
     * picker won the race (or it is no longer PENDING), which the caller surfaces as 409.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o "
            + "set o.status = com.ashu.shelflife.orders.OrderStatus.ASSIGNED, "
            + "    o.pickerId = :pickerId, "
            + "    o.version = o.version + 1 "
            + "where o.id = :orderId "
            + "  and o.status = com.ashu.shelflife.orders.OrderStatus.PENDING "
            + "  and o.version = :expectedVersion")
    int claim(@Param("orderId") Long orderId,
              @Param("pickerId") Long pickerId,
              @Param("expectedVersion") int expectedVersion);
}
