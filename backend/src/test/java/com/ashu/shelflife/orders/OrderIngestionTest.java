package com.ashu.shelflife.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ashu.shelflife.auth.dto.LoginRequest;
import com.ashu.shelflife.users.Role;
import com.ashu.shelflife.users.RoleRepository;
import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserRepository;
import com.ashu.shelflife.users.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.2 Order Ingestion: CSV + Excel upload, per-row validation report, atomic orders,
 * batch resilience, and admin-only RBAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderIngestionTest {

    private static final String ADMIN_PASSWORD = "Admin-pass1";
    private static final String PICKER_PASSWORD = "Picker-pass1";
    private static final String HEADER =
            "Order_ID,Customer_ID,Warehouse_ID,Item_SKU,Item_Name,Quantity_Ordered";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrderRepository orderRepository;

    private String adminToken;
    private Long warehouseId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("admin@ord.test", "Ord Admin", "CENTRAL_ADMIN", ADMIN_PASSWORD);
        adminToken = login("admin@ord.test", ADMIN_PASSWORD);

        warehouseId = jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES ('WH-1', 'Hub One', 'addr') RETURNING id", Long.class);
        insertProductWithShelf("SKU-1", "Aisle_A-Bay_01-Shelf_1");
        insertProductWithShelf("SKU-2", "Aisle_A-Bay_02-Shelf_1");
    }

    @Test
    void csvUploadGroupsRowsIntoOrdersAndItems() throws Exception {
        String csv = HEADER + "\n"
                + "O1,C1,WH-1,SKU-1,Widget,5\n"
                + "O1,C1,WH-1,SKU-2,Gadget,3\n"
                + "O2,C2,WH-1,SKU-1,Widget,2\n";

        uploadCsv("orders.csv", csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.ordersCreated").value(2));

        assertThat(orderRepository.findByOrderNumber("O1")).isPresent();
        assertThat(orderRepository.findByOrderNumber("O1").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(itemCount("O1")).isEqualTo(2);
        assertThat(itemCount("O2")).isEqualTo(1);
        // picked_quantity defaults to 0 for every ingested item.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_items WHERE picked_quantity <> 0", Integer.class))
                .isZero();
    }

    @Test
    void badRowsAreReportedAndDoNotFailTheBatch() throws Exception {
        // Pre-existing order to exercise the uniqueness rule.
        Order existing = new Order();
        existing.setOrderNumber("ODUP");
        existing.setCustomerId("C0");
        existing.setWarehouseId(warehouseId);
        existing.setStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(existing);

        String csv = HEADER + "\n"
                + "O3,C3,WH-1,SKU-1,Widget,1\n"          // ok
                + "O4,C4,WH-1,SKU-1,Widget,abc\n"        // bad quantity
                + "O5,C5,WH-NOPE,SKU-1,Widget,1\n"       // warehouse missing
                + "O6,C6,WH-1,SKU-NOSHELF,Thing,1\n"     // no shelf location
                + "ODUP,C7,WH-1,SKU-1,Widget,1\n";       // duplicate order id

        MvcResult result = uploadCsv("orders.csv", csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(4))
                .andExpect(jsonPath("$.ordersCreated").value(1))
                .andReturn();

        // Header is file line 1, so data rows are lines 2..6.
        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(errorForRow(report, 2)).isEqualTo("__success__");
        assertThat(errorForRow(report, 3)).contains("positive integer");
        assertThat(errorForRow(report, 4)).contains("does not exist");
        assertThat(errorForRow(report, 5)).contains("no shelf location");
        assertThat(errorForRow(report, 6)).contains("already exists");

        assertThat(orderRepository.findByOrderNumber("O3")).isPresent();
        assertThat(orderRepository.findByOrderNumber("O4")).isEmpty();
        assertThat(orderRepository.findByOrderNumber("O5")).isEmpty();
    }

    @Test
    void orderIsAtomicWhenOneLineItemFails() throws Exception {
        String csv = HEADER + "\n"
                + "O7,C7,WH-1,SKU-1,Widget,2\n"   // valid sibling
                + "O7,C7,WH-1,SKU-2,Gadget,0\n";  // invalid quantity (not positive)

        MvcResult result = uploadCsv("orders.csv", csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.ordersCreated").value(0))
                .andReturn();

        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(statusForRow(report, 2)).isEqualTo("SKIPPED");
        assertThat(statusForRow(report, 3)).isEqualTo("FAILED");
        assertThat(orderRepository.findByOrderNumber("O7")).isEmpty();
    }

    @Test
    void excelUploadIsSupported() throws Exception {
        byte[] xlsx = buildXlsx();
        MockMultipartFile file = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mockMvc.perform(multipart("/orders/upload").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.ordersCreated").value(1));

        assertThat(orderRepository.findByOrderNumber("O8")).isPresent();
        assertThat(itemCount("O8")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ordered_quantity FROM order_items oi JOIN orders o ON oi.order_id = o.id "
                        + "WHERE o.order_number = 'O8'", Integer.class)).isEqualTo(4);
    }

    @Test
    void invalidHeaderIsRejectedAsBadRequest() throws Exception {
        String csv = "Order_ID,Customer_ID,Warehouse_ID,Item_SKU,Quantity_Ordered\n"
                + "O9,C9,WH-1,SKU-1,1\n"; // missing Item_Name

        uploadCsv("orders.csv", csv)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Item_Name")));
    }

    @Test
    void hubPickerCannotUploadOrders() throws Exception {
        createUser("picker@ord.test", "Ord Picker", "HUB_PICKER", PICKER_PASSWORD);
        String pickerToken = login("picker@ord.test", PICKER_PASSWORD);

        MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv",
                (HEADER + "\nO1,C1,WH-1,SKU-1,Widget,1\n").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/orders/upload").file(file)
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions uploadCsv(String name, String csv)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/orders/upload").file(file)
                .header("Authorization", "Bearer " + adminToken));
    }

    private byte[] buildXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Orders");
            Row header = sheet.createRow(0);
            String[] cols = {"Order_ID", "Customer_ID", "Warehouse_ID", "Item_SKU", "Item_Name",
                    "Quantity_Ordered"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("O8");
            row.createCell(1).setCellValue("C8");
            row.createCell(2).setCellValue("WH-1");
            row.createCell(3).setCellValue("SKU-1");
            row.createCell(4).setCellValue("Widget");
            row.createCell(5).setCellValue(4); // numeric cell -> parser normalises to "4"
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int itemCount(String orderNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_items oi JOIN orders o ON oi.order_id = o.id "
                        + "WHERE o.order_number = ?", Integer.class, orderNumber);
    }

    private String errorForRow(JsonNode report, int rowNumber) {
        JsonNode row = findRow(report, rowNumber);
        if ("SUCCESS".equals(row.get("status").asText())) {
            return "__success__";
        }
        return row.get("error").asText();
    }

    private String statusForRow(JsonNode report, int rowNumber) {
        return findRow(report, rowNumber).get("status").asText();
    }

    private JsonNode findRow(JsonNode report, int rowNumber) {
        for (JsonNode row : report.get("rows")) {
            if (row.get("row").asInt() == rowNumber) {
                return row;
            }
        }
        throw new AssertionError("No report row for line " + rowNumber);
    }

    private void insertProductWithShelf(String sku, String locationCode) {
        jdbcTemplate.update(
                "INSERT INTO products (sku, name, barcode, unit) VALUES (?, ?, NULL, 'EA')", sku, sku);
        jdbcTemplate.update(
                "INSERT INTO shelf_locations (warehouse_id, sku, aisle, bay, shelf, location_code) "
                        + "VALUES (?, ?, 'A', '01', '1', ?)", warehouseId, sku, locationCode);
    }

    private void createUser(String email, String name, String roleName, String password) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(user);
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
