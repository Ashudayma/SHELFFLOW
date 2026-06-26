package com.ashu.shelflife.picking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ashu.shelflife.auth.dto.LoginRequest;
import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderItem;
import com.ashu.shelflife.orders.OrderItemStatus;
import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.orders.OrderStatus;
import com.ashu.shelflife.users.Role;
import com.ashu.shelflife.users.RoleRepository;
import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserRepository;
import com.ashu.shelflife.users.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.3 Scan-to-Pick: location-step enforcement, increment + pick_logs, skip/revisit,
 * order completion + session close, idempotency, and ownership/RBAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScanToPickTest {

    private static final String PICKER_PASSWORD = "Picker-pass1";
    private static final String ADMIN_PASSWORD = "Admin-pass1";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ObjectMapper objectMapper;

    private Long warehouseId;
    private Long pickerId;
    private String pickerToken;

    @BeforeEach
    void setUp() throws Exception {
        warehouseId = insertWarehouse("WH-SCAN", "Scan Hub");
        insertProductWithShelf("SKU-1", "Aisle_A-Bay_01-Shelf_1");
        insertProductWithShelf("SKU-2", "Aisle_B-Bay_01-Shelf_1");

        User picker = createUser("picker@scan.test", "Scan Picker", "HUB_PICKER", PICKER_PASSWORD);
        pickerId = picker.getId();
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                pickerId, warehouseId);
        pickerToken = login("picker@scan.test", PICKER_PASSWORD);
    }

    @Test
    void scanningWrongSkuIsRejected() throws Exception {
        Order order = claimedOrder("ORD-1", orderedQty("SKU-1", 1, "SKU-2", 1));

        // Current step is SKU-1 (Aisle_A...); scanning SKU-2 must be rejected.
        scan(order.getId(), "SKU-2", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expected SKU 'SKU-1'")));
    }

    @Test
    void fullPickIncrementsLogsAndCompletesOrder() throws Exception {
        Order order = claimedOrder("ORD-2", orderedQty("SKU-1", 2, "SKU-2", 1));
        Long id = order.getId();

        // First scan of SKU-1: picked 1/2, still the current step.
        scan(id, "SKU-1", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.pickedQuantity").value(1))
                .andExpect(jsonPath("$.item.status").value("PENDING"))
                .andExpect(jsonPath("$.orderStatus").value("PICKING"))
                .andExpect(jsonPath("$.nextStep.sku").value("SKU-1"));

        // Second scan: SKU-1 complete, advance to SKU-2.
        scan(id, "SKU-1", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PICKED"))
                .andExpect(jsonPath("$.nextStep.sku").value("SKU-2"));

        // Final scan: order complete, no next step.
        scan(id, "SKU-2", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PICKED"))
                .andExpect(jsonPath("$.orderStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.nextStep").isEmpty());

        // Flush Hibernate's pending dirty updates (order/session status) so the raw-SQL
        // assertions below see them within this transaction.
        orderRepository.flush();
        assertThat(orderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(pickLogCount(id)).isEqualTo(3);
        assertThat(sessionStatus(id)).isEqualTo("COMPLETED");
        assertThat(sessionEndTimeIsSet(id)).isTrue();
    }

    @Test
    void skippedItemIsBypassedThenRevisited() throws Exception {
        Order order = claimedOrder("ORD-3", orderedQty("SKU-1", 1, "SKU-2", 1));
        Long id = order.getId();
        Long sku1ItemId = itemId(order, "SKU-1");

        // Skip the current step (SKU-1) -> advance to SKU-2.
        mockMvc.perform(post("/orders/" + id + "/items/" + sku1ItemId + "/skip")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("SKIPPED"))
                .andExpect(jsonPath("$.nextStep.sku").value("SKU-2"));

        // Pick SKU-2; order not complete (SKU-1 still outstanding) and route returns to SKU-1.
        scan(id, "SKU-2", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PICKING"))
                .andExpect(jsonPath("$.nextStep.sku").value("SKU-1"));

        // Revisit the skipped item and finish.
        scan(id, "SKU-1", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PICKED"))
                .andExpect(jsonPath("$.orderStatus").value("COMPLETED"));

        assertThat(orderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void duplicateScanIdIsAnIdempotentNoOp() throws Exception {
        Order order = claimedOrder("ORD-4", orderedQty("SKU-1", 2));
        Long id = order.getId();

        scan(id, "SKU-1", "scan-key-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.pickedQuantity").value(1))
                .andExpect(jsonPath("$.duplicate").value(false));

        // Same scanId again: no second increment.
        scan(id, "SKU-1", "scan-key-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.pickedQuantity").value(1))
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(pickLogCount(id)).isEqualTo(1);
    }

    @Test
    void scanningAnOrderYouDoNotOwnIsForbidden() throws Exception {
        Order order = claimedOrder("ORD-5", orderedQty("SKU-1", 1));

        createUser("other@scan.test", "Other Picker", "HUB_PICKER", PICKER_PASSWORD);
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) "
                        + "SELECT id, ? FROM users WHERE email = 'other@scan.test'", warehouseId);
        String otherToken = login("other@scan.test", PICKER_PASSWORD);

        mockMvc.perform(post("/scan")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + order.getId() + ",\"sku\":\"SKU-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void scanningAnOrderInAForeignWarehouseIsForbidden() throws Exception {
        // An order living in a warehouse the picker is NOT mapped to. The picker tries to scan
        // it by passing its ID directly — warehouse data isolation must reject it with 403,
        // independent of order ownership (the scope check runs before the ownership check).
        Long foreignWarehouseId = insertWarehouse("WH-FOREIGN", "Foreign Hub");
        Order foreign = new Order();
        foreign.setOrderNumber("ORD-FOREIGN");
        foreign.setCustomerId("CUST");
        foreign.setWarehouseId(foreignWarehouseId);
        foreign.setStatus(OrderStatus.PENDING);
        OrderItem item = new OrderItem();
        item.setSku("SKU-1");
        item.setItemName("SKU-1 name");
        item.setOrderedQuantity(1);
        item.setPickedQuantity(0);
        item.setStatus(OrderItemStatus.PENDING);
        foreign.addItem(item);
        Long foreignOrderId = orderRepository.saveAndFlush(foreign).getId();

        scan(foreignOrderId, "SKU-1", null)
                .andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions scan(Long orderId, String sku, String scanId)
            throws Exception {
        String body = scanId == null
                ? "{\"orderId\":" + orderId + ",\"sku\":\"" + sku + "\"}"
                : "{\"orderId\":" + orderId + ",\"sku\":\"" + sku + "\",\"scanId\":\"" + scanId + "\"}";
        return mockMvc.perform(post("/scan")
                .header("Authorization", "Bearer " + pickerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Map<String, Integer> orderedQty(Object... skuThenQty) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < skuThenQty.length; i += 2) {
            map.put((String) skuThenQty[i], (Integer) skuThenQty[i + 1]);
        }
        return map;
    }

    private Order claimedOrder(String orderNumber, Map<String, Integer> items) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomerId("CUST");
        order.setWarehouseId(warehouseId);
        order.setPickerId(pickerId);
        order.setStatus(OrderStatus.ASSIGNED);
        items.forEach((sku, qty) -> {
            OrderItem item = new OrderItem();
            item.setSku(sku);
            item.setItemName(sku + " name");
            item.setOrderedQuantity(qty);
            item.setPickedQuantity(0);
            item.setStatus(OrderItemStatus.PENDING);
            order.addItem(item);
        });
        return orderRepository.saveAndFlush(order);
    }

    private Long itemId(Order order, String sku) {
        return order.getItems().stream().filter(it -> it.getSku().equals(sku))
                .findFirst().orElseThrow().getId();
    }

    private int pickLogCount(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pick_logs pl JOIN pick_sessions ps ON pl.session_id = ps.id "
                        + "WHERE ps.order_id = ?", Integer.class, orderId);
    }

    private String sessionStatus(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM pick_sessions WHERE order_id = ?", String.class, orderId);
    }

    private boolean sessionEndTimeIsSet(Long orderId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT end_time IS NOT NULL FROM pick_sessions WHERE order_id = ?",
                Boolean.class, orderId));
    }

    private Long insertWarehouse(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES (?, ?, 'addr') RETURNING id", Long.class, code, name);
    }

    private void insertProductWithShelf(String sku, String locationCode) {
        jdbcTemplate.update(
                "INSERT INTO products (sku, name, barcode, unit) VALUES (?, ?, NULL, 'EA')", sku, sku);
        jdbcTemplate.update(
                "INSERT INTO shelf_locations (warehouse_id, sku, aisle, bay, shelf, location_code) "
                        + "VALUES (?, ?, 'A', '01', '1', ?)", warehouseId, sku, locationCode);
    }

    private User createUser(String email, String name, String roleName, String password) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(email, password));
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
