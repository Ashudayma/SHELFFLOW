package com.ashu.shelflife.picking;

import com.ashu.shelflife.audit.AuditAction;
import com.ashu.shelflife.audit.AuditEvent;
import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.inventory.ShelfLocation;
import com.ashu.shelflife.inventory.ShelfLocationRepository;
import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderItem;
import com.ashu.shelflife.orders.OrderItemStatus;
import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.orders.OrderStatus;
import com.ashu.shelflife.picking.dto.ScanRequest;
import com.ashu.shelflife.picking.dto.ScanResponse;
import com.ashu.shelflife.picking.dto.ScannedItem;
import com.ashu.shelflife.picking.dto.StepView;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.warehouse.WarehouseScope;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.3 Scan-to-Pick. The scan path is performance-critical (BRD §4 "resolve instantly"),
 * so the transaction does only the essentials — validate, update the item, write one pick_log,
 * and (on completion) close the session. Anything non-essential (audit trail, §4) is deferred
 * to the async mechanism in a later step, not done inline here.
 */
@Service
public class ScanService {

    private final OrderRepository orderRepository;
    private final PickSessionRepository pickSessionRepository;
    private final PickLogRepository pickLogRepository;
    private final ShelfLocationRepository shelfLocationRepository;
    private final WarehouseScope warehouseScope;
    private final ApplicationEventPublisher eventPublisher;

    public ScanService(OrderRepository orderRepository,
                       PickSessionRepository pickSessionRepository,
                       PickLogRepository pickLogRepository,
                       ShelfLocationRepository shelfLocationRepository,
                       WarehouseScope warehouseScope,
                       ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.pickSessionRepository = pickSessionRepository;
        this.pickLogRepository = pickLogRepository;
        this.shelfLocationRepository = shelfLocationRepository;
        this.warehouseScope = warehouseScope;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ScanResponse scan(AuthenticatedUser picker, ScanRequest request) {
        String scanId = normalize(request.scanId());

        // Idempotency: a previously-recorded scanId is a no-op replay.
        if (scanId != null && pickLogRepository.findByScanId(scanId).isPresent()) {
            Order order = loadOwnedOrder(request.orderId(), picker);
            Map<String, String> codes = locationCodes(order);
            return buildResponse(order, codes, findBySku(order, request.sku()), true);
        }

        Order order = loadOwnedOrder(request.orderId(), picker);
        ensureActive(order);
        PickSession session = openOrGetSession(order, picker);

        Map<String, String> codes = locationCodes(order);
        OrderItem step = currentStep(order, codes)
                .orElseThrow(() -> new ConflictException("No remaining items to pick for this order."));

        if (!step.getSku().equals(request.sku())) {
            String location = codes.get(step.getSku());
            throw new ConflictException("Scan rejected: expected SKU '" + step.getSku() + "'"
                    + (location != null ? " at location '" + location + "'" : "")
                    + ", but scanned '" + request.sku()
                    + "'. Complete or skip the current step first.");
        }

        // Correct scan: increment by exactly 1 and log it.
        step.setPickedQuantity(step.getPickedQuantity() + 1);
        recordPickLog(session.getId(), step.getSku(), codes.get(step.getSku()), scanId);

        String location = codes.get(step.getSku());
        boolean itemPicked;
        if (step.getPickedQuantity() >= step.getOrderedQuantity()) {
            step.setStatus(OrderItemStatus.PICKED);
            itemPicked = true;
        } else {
            // A revisited SKIPPED item is now actively being picked again.
            step.setStatus(OrderItemStatus.PENDING);
            itemPicked = false;
        }

        boolean orderCompleted = allPicked(order);
        if (orderCompleted) {
            order.setStatus(OrderStatus.COMPLETED);
            session.setStatus(PickSessionStatus.COMPLETED);
            session.setEndTime(OffsetDateTime.now());
        }

        // Audit is a cross-cutting concern: publish events and let the @Async listener write
        // audit_logs AFTER this transaction commits — never inline on the scan path (BRD §4).
        eventPublisher.publishEvent(AuditEvent.of(
                AuditAction.SCAN, picker.id(), order.getId(), step.getSku(), location));
        if (itemPicked) {
            eventPublisher.publishEvent(AuditEvent.of(
                    AuditAction.ITEM_PICKED, picker.id(), order.getId(), step.getSku(), location));
        }
        if (orderCompleted) {
            eventPublisher.publishEvent(AuditEvent.of(
                    AuditAction.ORDER_COMPLETED, picker.id(), order.getId(), null, null));
        }

        return buildResponse(order, codes, step, false);
    }

    /**
     * BRD 3.3: skip the current item so the picker is not blocked. The item is marked SKIPPED
     * and the route advances; once all PENDING items are done the route returns to SKIPPED ones.
     */
    @Transactional
    public ScanResponse skip(AuthenticatedUser picker, Long orderId, Long itemId) {
        Order order = loadOwnedOrder(orderId, picker);
        ensureActive(order);

        OrderItem item = order.getItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Item " + itemId + " not found in order " + orderId + "."));

        if (item.getStatus() == OrderItemStatus.PICKED) {
            throw new ConflictException("Item " + itemId + " is already picked and cannot be skipped.");
        }
        item.setStatus(OrderItemStatus.SKIPPED);

        Map<String, String> codes = locationCodes(order);
        eventPublisher.publishEvent(AuditEvent.of(
                AuditAction.SKIP, picker.id(), order.getId(), item.getSku(), codes.get(item.getSku())));

        return buildResponse(order, codes, item, false);
    }

    // --- internals ----------------------------------------------------------------------

    /**
     * The current location step: the next PENDING item in route (location_code) order. When no
     * PENDING items remain, the route returns to the first SKIPPED item so it can be revisited.
     */
    private Optional<OrderItem> currentStep(Order order, Map<String, String> codes) {
        Comparator<OrderItem> byRoute = Comparator.comparing(
                it -> codes.get(it.getSku()), Comparator.nullsLast(Comparator.naturalOrder()));

        Optional<OrderItem> pending = order.getItems().stream()
                .filter(it -> it.getStatus() == OrderItemStatus.PENDING)
                .min(byRoute);
        if (pending.isPresent()) {
            return pending;
        }
        return order.getItems().stream()
                .filter(it -> it.getStatus() == OrderItemStatus.SKIPPED)
                .min(byRoute);
    }

    private boolean allPicked(Order order) {
        return order.getItems().stream().allMatch(it -> it.getStatus() == OrderItemStatus.PICKED);
    }

    private Order loadOwnedOrder(Long orderId, AuthenticatedUser picker) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        warehouseScope.assertAccessible(picker, order.getWarehouseId());
        if (order.getPickerId() == null || !order.getPickerId().equals(picker.id())) {
            throw new AccessDeniedException("This order is not assigned to you.");
        }
        return order;
    }

    private void ensureActive(Order order) {
        switch (order.getStatus()) {
            case ASSIGNED, PICKING -> {
                // ok — claimed and pickable
            }
            case PENDING -> throw new ConflictException("Order is not claimed. Claim it before picking.");
            default -> throw new ConflictException(
                    "Order is already " + order.getStatus() + "; it can no longer be picked.");
        }
    }

    private PickSession openOrGetSession(Order order, AuthenticatedUser picker) {
        PickSession session = pickSessionRepository
                .findByOrderIdAndStatus(order.getId(), PickSessionStatus.IN_PROGRESS)
                .orElseGet(() -> {
                    PickSession created = new PickSession();
                    created.setOrderId(order.getId());
                    created.setPickerId(picker.id());
                    created.setStartTime(OffsetDateTime.now());
                    created.setStatus(PickSessionStatus.IN_PROGRESS);
                    return pickSessionRepository.save(created);
                });
        if (order.getStatus() == OrderStatus.ASSIGNED) {
            order.setStatus(OrderStatus.PICKING);
        }
        return session;
    }

    private void recordPickLog(Long sessionId, String sku, String location, String scanId) {
        PickLog log = new PickLog();
        log.setSessionId(sessionId);
        log.setSku(sku);
        log.setQuantity(1);
        log.setLocation(location);
        log.setScanId(scanId);
        pickLogRepository.save(log);
    }

    private Map<String, String> locationCodes(Order order) {
        List<String> skus = order.getItems().stream().map(OrderItem::getSku).distinct().toList();
        if (skus.isEmpty()) {
            return Map.of();
        }
        return shelfLocationRepository.findByWarehouseIdAndSkuIn(order.getWarehouseId(), skus).stream()
                .collect(Collectors.toMap(ShelfLocation::getSku, ShelfLocation::getLocationCode,
                        (a, b) -> a));
    }

    private OrderItem findBySku(Order order, String sku) {
        return order.getItems().stream()
                .filter(it -> it.getSku().equals(sku))
                .findFirst()
                .orElse(null);
    }

    private ScanResponse buildResponse(Order order, Map<String, String> codes,
                                       OrderItem affected, boolean duplicate) {
        ScannedItem item = affected == null ? null : ScannedItem.from(affected);
        StepView next = currentStep(order, codes)
                .map(s -> new StepView(s.getId(), s.getSku(), s.getItemName(),
                        codes.get(s.getSku()), s.getOrderedQuantity(), s.getPickedQuantity()))
                .orElse(null);
        return new ScanResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                item, next, duplicate);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
