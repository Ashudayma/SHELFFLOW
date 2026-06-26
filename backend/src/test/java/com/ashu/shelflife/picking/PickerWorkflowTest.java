package com.ashu.shelflife.picking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
 * BRD 3.3 picker workflow: warehouse entry, dashboard scoping, order claim (and re-claim
 * conflict), and the BRD 3.2 routing engine ordering. Concurrency is covered separately in
 * {@link OrderClaimConcurrencyTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PickerWorkflowTest {

    private static final String PICKER_PASSWORD = "Picker-pass1";
    private static final String ADMIN_PASSWORD = "Admin-pass1";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ObjectMapper objectMapper;

    private Long warehouse1;
    private Long warehouse2;
    private Long wh1OrderId;
    private Long wh2OrderId;
    private String pickerToken;
    private Long pickerId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        warehouse1 = insertWarehouse("WH-1", "Hub One");
        warehouse2 = insertWarehouse("WH-2", "Hub Two");

        // Four SKUs with shelf codes that should sort A1 -> A2 -> B1 -> C2.
        insertProductWithShelf(warehouse1, "SKU-1", "Aisle_A-Bay_01-Shelf_1");
        insertProductWithShelf(warehouse1, "SKU-2", "Aisle_A-Bay_02-Shelf_1");
        insertProductWithShelf(warehouse1, "SKU-3", "Aisle_B-Bay_01-Shelf_1");
        insertProductWithShelf(warehouse1, "SKU-4", "Aisle_C-Bay_02-Shelf_1");

        // Items inserted out of order to prove the routing engine sorts them.
        Order wh1Order = pendingOrder("ORD-WH1", warehouse1,
                List.of("SKU-4", "SKU-2", "SKU-3", "SKU-1"));
        wh1OrderId = orderRepository.saveAndFlush(wh1Order).getId();

        wh2OrderId = orderRepository.saveAndFlush(pendingOrder("ORD-WH2", warehouse2, List.of()))
                .getId();

        User picker = createUser("picker@pk.test", "Pick One", "HUB_PICKER", PICKER_PASSWORD);
        pickerId = picker.getId();
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                pickerId, warehouse1);
        pickerToken = login("picker@pk.test", PICKER_PASSWORD);

        createUser("admin@pk.test", "Admin One", "CENTRAL_ADMIN", ADMIN_PASSWORD);
        adminToken = login("admin@pk.test", ADMIN_PASSWORD);
    }

    @Test
    void selectWarehouseThenDashboardIsScopedToActiveWarehouse() throws Exception {
        selectWarehouse(warehouse1).andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouse1.intValue()))
                .andExpect(jsonPath("$.warehouseCode").value("WH-1"));

        MvcResult result = mockMvc.perform(get("/picker/dashboard")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeWarehouseId").value(warehouse1.intValue()))
                .andReturn();

        List<String> available = orderNumbers(result, "available");
        assertThat(available).contains("ORD-WH1");
        assertThat(available).doesNotContain("ORD-WH2"); // foreign-warehouse order never shown
    }

    @Test
    void pickerCanListTheirMappedWarehouses() throws Exception {
        mockMvc.perform(get("/picker/warehouses").header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].warehouseCode").value("WH-1"));
    }

    @Test
    void selectingAForeignWarehouseIsRejected() throws Exception {
        selectWarehouse(warehouse2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void dashboardWithoutAWarehouseSelectionIsConflict() throws Exception {
        mockMvc.perform(get("/picker/dashboard").header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active warehouse")));
    }

    @Test
    void claimSucceedsThenSecondClaimConflicts() throws Exception {
        selectWarehouse(warehouse1).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + wh1OrderId + "/claim")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.pickerId").value(pickerId.intValue()))
                .andExpect(jsonPath("$.version").value(1));

        // Re-claim of a now-ASSIGNED order -> 409.
        mockMvc.perform(post("/orders/" + wh1OrderId + "/claim")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already claimed")));

        // The claimed order moves from available -> current.
        MvcResult dash = mockMvc.perform(get("/picker/dashboard")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(orderNumbers(dash, "available")).doesNotContain("ORD-WH1");
        assertThat(orderNumbers(dash, "current")).contains("ORD-WH1");
    }

    @Test
    void claimingAnOrderInAForeignWarehouseIsForbidden() throws Exception {
        mockMvc.perform(post("/orders/" + wh2OrderId + "/claim")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void routeReturnsItemsSortedByLocationCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/orders/" + wh1OrderId + "/route")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].sequence").value(1))
                .andExpect(jsonPath("$.steps[0].status").value("PENDING"))
                .andReturn();

        JsonNode steps = objectMapper.readTree(result.getResponse().getContentAsString()).get("steps");
        List<String> codes = new ArrayList<>();
        steps.forEach(s -> codes.add(s.get("locationCode").asText()));
        assertThat(codes).containsExactly(
                "Aisle_A-Bay_01-Shelf_1", "Aisle_A-Bay_02-Shelf_1",
                "Aisle_B-Bay_01-Shelf_1", "Aisle_C-Bay_02-Shelf_1");
    }

    @Test
    void routingAForeignOrderIsForbiddenForPicker() throws Exception {
        mockMvc.perform(get("/orders/" + wh2OrderId + "/route")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotSelectAWarehouse() throws Exception {
        mockMvc.perform(post("/picker/select-warehouse")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":" + warehouse1 + "}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions selectWarehouse(Long warehouseId)
            throws Exception {
        return mockMvc.perform(post("/picker/select-warehouse")
                .header("Authorization", "Bearer " + pickerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\":" + warehouseId + "}"));
    }

    private List<String> orderNumbers(MvcResult result, String listName) throws Exception {
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString()).get(listName);
        List<String> numbers = new ArrayList<>();
        list.forEach(node -> numbers.add(node.get("orderNumber").asText()));
        return numbers;
    }

    private Order pendingOrder(String orderNumber, Long warehouseId, List<String> skus) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomerId("CUST");
        order.setWarehouseId(warehouseId);
        order.setStatus(OrderStatus.PENDING);
        for (String sku : skus) {
            OrderItem item = new OrderItem();
            item.setSku(sku);
            item.setItemName(sku + " name");
            item.setOrderedQuantity(1);
            item.setPickedQuantity(0);
            item.setStatus(OrderItemStatus.PENDING);
            order.addItem(item);
        }
        return order;
    }

    private Long insertWarehouse(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES (?, ?, 'addr') RETURNING id", Long.class, code, name);
    }

    private void insertProductWithShelf(Long warehouseId, String sku, String locationCode) {
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
