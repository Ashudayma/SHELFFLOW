package com.ashu.shelflife.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ashu.shelflife.audit.AuditAction;
import com.ashu.shelflife.audit.AuditEvent;
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
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.4 Dispatch Report: exact field/rate output, optional filters, CSV/Excel export, the
 * defensive zero-ordered case, RBAC, and the DOWNLOAD_REPORT audit event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class ReportTest {

    private static final String ADMIN_PASSWORD = "Admin-pass1";
    private static final String PICKER_PASSWORD = "Picker-pass1";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationEvents events;

    private String adminToken;
    private Long adminId;
    private String pickerToken;
    private Long pickerId;
    private Long whR;
    private Long whZ;

    @BeforeEach
    void setUp() throws Exception {
        User admin = createUser("admin@rep.test", "Rep Admin", "CENTRAL_ADMIN", ADMIN_PASSWORD);
        adminId = admin.getId();
        adminToken = login("admin@rep.test", ADMIN_PASSWORD);

        User picker = createUser("picker@rep.test", "Rep Picker", "HUB_PICKER", PICKER_PASSWORD);
        pickerId = picker.getId();

        whR = insertWarehouse("WH-R", "Hub R");
        Long whX = insertWarehouse("WH-X", "Hub X");
        whZ = insertWarehouse("WH-Z", "Hub Z");
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                pickerId, whR);
        pickerToken = login("picker@rep.test", PICKER_PASSWORD);

        product("SKU-1");
        product("SKU-2");

        // ORD-A: WH-R, picker — SKU-1 2/2 (1.0), SKU-2 1/4 (0.25)
        saveOrder("ORD-A", whR, pickerId, OrderStatus.COMPLETED,
                item("SKU-1", 2, 2), item("SKU-2", 4, 1));
        // ORD-B: WH-R, picker — SKU-1 0/3 (0.0)
        saveOrder("ORD-B", whR, pickerId, OrderStatus.PICKING, item("SKU-1", 3, 0));
        // ORD-C: WH-X, unclaimed — excluded by WH-R / picker filters
        saveOrder("ORD-C", whX, null, OrderStatus.PENDING, item("SKU-1", 1, 1));
        // ORD-Z: WH-Z, ordered quantity 0 (defensive edge; bypasses upload validation)
        saveOrder("ORD-Z", whZ, null, OrderStatus.PENDING, item("SKU-1", 0, 0));
    }

    @Test
    void jsonReportHasExactFieldsAndDynamicFulfillmentRate() throws Exception {
        mockMvc.perform(get("/report").param("warehouse", "WH-R")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // ordered by Order_ID, Item_SKU → ORD-A/SKU-1, ORD-A/SKU-2, ORD-B/SKU-1
                .andExpect(jsonPath("$[0]['Order_ID']").value("ORD-A"))
                .andExpect(jsonPath("$[0]['Picker_ID']").value(pickerId.intValue()))
                .andExpect(jsonPath("$[0]['Warehouse_ID']").value("WH-R"))
                .andExpect(jsonPath("$[0]['Item_SKU']").value("SKU-1"))
                .andExpect(jsonPath("$[0]['Quantity_Ordered']").value(2))
                .andExpect(jsonPath("$[0]['Quantity_Picked']").value(2))
                .andExpect(jsonPath("$[0]['Fulfillment_Rate']").value(1.0))
                .andExpect(jsonPath("$[1]['Item_SKU']").value("SKU-2"))
                .andExpect(jsonPath("$[1]['Fulfillment_Rate']").value(0.25))
                .andExpect(jsonPath("$[2]['Order_ID']").value("ORD-B"))
                .andExpect(jsonPath("$[2]['Fulfillment_Rate']").value(0.0));
    }

    @Test
    void zeroOrderedQuantityYieldsZeroRateNotError() throws Exception {
        mockMvc.perform(get("/report").param("warehouse", "WH-Z")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]['Quantity_Ordered']").value(0))
                .andExpect(jsonPath("$[0]['Fulfillment_Rate']").value(0.0));
    }

    @Test
    void pickerFilterRestrictsToThatPicker() throws Exception {
        mockMvc.perform(get("/report").param("picker", String.valueOf(pickerId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)) // ORD-A (2) + ORD-B (1); ORD-C/ORD-Z unclaimed
                .andExpect(jsonPath("$[*]['Picker_ID']", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(pickerId.intValue()))));
    }

    @Test
    void dateFilterMatchesTodayAndExcludesOtherDays() throws Exception {
        mockMvc.perform(get("/report")
                        .param("warehouse", "WH-R")
                        .param("date", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/report")
                        .param("date", "2000-01-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void csvExportHasHeaderAndRows() throws Exception {
        MvcResult result = mockMvc.perform(get("/report")
                        .param("warehouse", "WH-R").param("format", "csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("dispatch-report.csv")))
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv).contains("Order_ID", "Picker_ID", "Warehouse_ID", "Fulfillment_Rate");
        assertThat(csv).contains("ORD-A");
        assertThat(csv).contains("1.0000"); // SKU-1 fully picked
        assertThat(csv).contains("0.2500"); // SKU-2 partially picked
    }

    @Test
    void xlsxExportIsAValidWorkbook() throws Exception {
        MvcResult result = mockMvc.perform(get("/report")
                        .param("warehouse", "WH-R").param("format", "xlsx")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("dispatch-report.xlsx")))
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Order_ID");
            assertThat(sheet.getLastRowNum()).isEqualTo(3); // header + 3 data rows
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(1.0);
        }
    }

    @Test
    void reportAccessPublishesDownloadReportAuditEvent() throws Exception {
        mockMvc.perform(get("/report").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        long downloads = events.stream(AuditEvent.class)
                .filter(e -> e.action() == AuditAction.DOWNLOAD_REPORT)
                .filter(e -> adminId.equals(e.userId()))
                .count();
        assertThat(downloads).isEqualTo(1);
    }

    @Test
    void hubPickerCannotAccessReport() throws Exception {
        mockMvc.perform(get("/report").header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------

    private OrderItem item(String sku, int ordered, int picked) {
        OrderItem oi = new OrderItem();
        oi.setSku(sku);
        oi.setItemName(sku + " name");
        oi.setOrderedQuantity(ordered);
        oi.setPickedQuantity(picked);
        oi.setStatus(picked >= ordered && ordered > 0 ? OrderItemStatus.PICKED : OrderItemStatus.PENDING);
        return oi;
    }

    private void saveOrder(String number, Long warehouseId, Long owner, OrderStatus status,
                           OrderItem... items) {
        Order order = new Order();
        order.setOrderNumber(number);
        order.setCustomerId("CUST");
        order.setWarehouseId(warehouseId);
        order.setPickerId(owner);
        order.setStatus(status);
        for (OrderItem it : items) {
            order.addItem(it);
        }
        orderRepository.saveAndFlush(order);
    }

    private Long insertWarehouse(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES (?, ?, 'addr') RETURNING id", Long.class, code, name);
    }

    private void product(String sku) {
        jdbcTemplate.update(
                "INSERT INTO products (sku, name, barcode, unit) VALUES (?, ?, NULL, 'EA')", sku, sku);
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
