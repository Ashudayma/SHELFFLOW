package com.ashu.shelflife.orders;

import com.ashu.shelflife.inventory.ShelfLocationRepository;
import com.ashu.shelflife.orders.dto.OrderUploadReport;
import com.ashu.shelflife.orders.dto.OrderUploadRowResult;
import com.ashu.shelflife.orders.ingest.OrderFileParser;
import com.ashu.shelflife.orders.ingest.OrderFileParserFactory;
import com.ashu.shelflife.orders.ingest.RawOrderRow;
import com.ashu.shelflife.warehouse.Warehouse;
import com.ashu.shelflife.warehouse.WarehouseRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.2 Order Ingestion. Parses a CSV/Excel upload, validates each row independently, and
 * persists orders grouped by Order_ID. The batch never fails wholesale: every data row gets a
 * status in the returned {@link OrderUploadReport}.
 *
 * <p>Orders are atomic — an order is persisted only if all of its rows are valid and mutually
 * consistent. If any row of an Order_ID fails, the order is not created: the bad row(s) report
 * FAILED and the valid sibling rows report SKIPPED.
 */
@Service
public class OrderIngestionService {

    private final OrderFileParserFactory parserFactory;
    private final OrderRepository orderRepository;
    private final WarehouseRepository warehouseRepository;
    private final ShelfLocationRepository shelfLocationRepository;

    public OrderIngestionService(OrderFileParserFactory parserFactory,
                                 OrderRepository orderRepository,
                                 WarehouseRepository warehouseRepository,
                                 ShelfLocationRepository shelfLocationRepository) {
        this.parserFactory = parserFactory;
        this.orderRepository = orderRepository;
        this.warehouseRepository = warehouseRepository;
        this.shelfLocationRepository = shelfLocationRepository;
    }

    /** Resolved per-row validation outcome. {@code error == null} means the row is valid. */
    private record RowState(RawOrderRow raw, String error, Long warehouseId, Integer quantity) {
        boolean valid() {
            return error == null;
        }
    }

    @Transactional
    public OrderUploadReport ingest(String filename, byte[] content) {
        OrderFileParser parser = parserFactory.forFile(filename);
        List<RawOrderRow> rawRows = parser.parse(content);

        Map<String, Optional<Warehouse>> warehouseCache = new HashMap<>();
        Map<String, Boolean> shelfCache = new HashMap<>();
        Map<String, Boolean> orderExistsCache = new HashMap<>();

        List<RowState> states = rawRows.stream()
                .map(raw -> validateRow(raw, warehouseCache, shelfCache, orderExistsCache))
                .toList();

        Map<Integer, OrderUploadRowResult> results = new HashMap<>();

        // Group by Order_ID. Rows with a blank Order_ID can't be grouped — fail them directly.
        Map<String, List<RowState>> groups = new LinkedHashMap<>();
        for (RowState state : states) {
            if (state.raw().orderId().isBlank()) {
                results.put(state.raw().rowNumber(),
                        OrderUploadRowResult.failed(state.raw().rowNumber(), "", state.error()));
            } else {
                groups.computeIfAbsent(state.raw().orderId(), k -> new ArrayList<>()).add(state);
            }
        }

        int ordersCreated = 0;
        for (Map.Entry<String, List<RowState>> entry : groups.entrySet()) {
            ordersCreated += processGroup(entry.getKey(), entry.getValue(), results);
        }

        List<OrderUploadRowResult> ordered = results.values().stream()
                .sorted(Comparator.comparingInt(OrderUploadRowResult::row))
                .toList();
        return OrderUploadReport.of(rawRows.size(), ordersCreated, ordered);
    }

    /** @return 1 if the group produced an order, else 0. */
    private int processGroup(String orderId, List<RowState> group,
                             Map<Integer, OrderUploadRowResult> results) {
        List<RowState> failedRows = group.stream().filter(s -> !s.valid()).toList();

        if (!failedRows.isEmpty()) {
            String failingLines = failedRows.stream()
                    .map(s -> String.valueOf(s.raw().rowNumber()))
                    .collect(Collectors.joining(", "));
            for (RowState s : group) {
                if (!s.valid()) {
                    results.put(s.raw().rowNumber(),
                            OrderUploadRowResult.failed(s.raw().rowNumber(), orderId, s.error()));
                } else {
                    results.put(s.raw().rowNumber(), OrderUploadRowResult.skipped(
                            s.raw().rowNumber(), orderId,
                            "Order '" + orderId + "' not ingested: row(s) " + failingLines
                                    + " failed validation."));
                }
            }
            return 0;
        }

        // All rows valid — require a single Customer_ID and Warehouse_ID across the order.
        long distinctCustomers = group.stream().map(s -> s.raw().customerId()).distinct().count();
        long distinctWarehouses = group.stream().map(s -> s.raw().warehouseId()).distinct().count();
        if (distinctCustomers > 1 || distinctWarehouses > 1) {
            String field = distinctWarehouses > 1 ? "Warehouse_ID" : "Customer_ID";
            for (RowState s : group) {
                results.put(s.raw().rowNumber(), OrderUploadRowResult.failed(
                        s.raw().rowNumber(), orderId,
                        "Inconsistent " + field + " across rows for Order_ID '" + orderId + "'."));
            }
            return 0;
        }

        persistOrder(orderId, group);
        for (RowState s : group) {
            results.put(s.raw().rowNumber(), OrderUploadRowResult.success(s.raw().rowNumber(), orderId));
        }
        return 1;
    }

    private void persistOrder(String orderId, List<RowState> group) {
        RowState first = group.get(0);
        Order order = new Order();
        order.setOrderNumber(orderId);
        order.setCustomerId(first.raw().customerId());
        order.setWarehouseId(first.warehouseId());
        order.setStatus(OrderStatus.PENDING);

        for (RowState s : group) {
            OrderItem item = new OrderItem();
            item.setSku(s.raw().itemSku());
            item.setItemName(s.raw().itemName());
            item.setOrderedQuantity(s.quantity());
            item.setPickedQuantity(0);
            item.setStatus(OrderItemStatus.PENDING);
            order.addItem(item);
        }
        orderRepository.save(order);
    }

    private RowState validateRow(RawOrderRow raw,
                                 Map<String, Optional<Warehouse>> warehouseCache,
                                 Map<String, Boolean> shelfCache,
                                 Map<String, Boolean> orderExistsCache) {
        if (raw.orderId().isBlank()) {
            return new RowState(raw, "Order_ID is required.", null, null);
        }
        if (raw.customerId().isBlank()) {
            return new RowState(raw, "Customer_ID is required.", null, null);
        }
        if (raw.warehouseId().isBlank()) {
            return new RowState(raw, "Warehouse_ID is required.", null, null);
        }
        if (raw.itemSku().isBlank()) {
            return new RowState(raw, "Item_SKU is required.", null, null);
        }
        if (raw.itemName().isBlank()) {
            return new RowState(raw, "Item_Name is required.", null, null);
        }

        Integer quantity = parsePositiveInt(raw.quantityOrdered());
        if (quantity == null) {
            return new RowState(raw,
                    "Quantity_Ordered must be a positive integer (got '" + raw.quantityOrdered() + "').",
                    null, null);
        }

        boolean orderExists = orderExistsCache.computeIfAbsent(
                raw.orderId(), orderRepository::existsByOrderNumber);
        if (orderExists) {
            return new RowState(raw, "Order_ID already exists: " + raw.orderId() + ".", null, null);
        }

        Optional<Warehouse> warehouse = warehouseCache.computeIfAbsent(
                raw.warehouseId(), warehouseRepository::findByWarehouseCode);
        if (warehouse.isEmpty()) {
            return new RowState(raw, "Warehouse_ID does not exist: " + raw.warehouseId() + ".", null, null);
        }

        Long warehouseId = warehouse.get().getId();
        boolean hasShelf = shelfCache.computeIfAbsent(
                warehouseId + "|" + raw.itemSku(),
                k -> shelfLocationRepository.existsByWarehouseIdAndSku(warehouseId, raw.itemSku()));
        if (!hasShelf) {
            return new RowState(raw,
                    "Item_SKU '" + raw.itemSku() + "' has no shelf location in warehouse '"
                            + raw.warehouseId() + "'.",
                    null, null);
        }

        return new RowState(raw, null, warehouseId, quantity);
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
