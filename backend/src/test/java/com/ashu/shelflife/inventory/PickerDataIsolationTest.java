package com.ashu.shelflife.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Proves BRD 3.1 "Data Isolation": a HUB_PICKER's warehouse scope is taken from their JWT
 * claims, never from a client-supplied request parameter.
 *
 * <p>Setup: two warehouses (A and B), one shelf location in each; a picker assigned to A only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PickerDataIsolationTest {

    private static final String PASSWORD = "S3cret-pass!";
    private static final String SKU = "SKU-ISO-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private Long warehouseA;
    private Long warehouseB;

    @BeforeEach
    void setUp() {
        warehouseA = insertWarehouse("WH-A", "Hub A");
        warehouseB = insertWarehouse("WH-B", "Hub B");

        jdbcTemplate.update(
                "INSERT INTO products (sku, name, barcode, unit) VALUES (?, 'Widget', 'BC-1', 'EA')", SKU);
        insertShelfLocation(warehouseA, "Aisle_A-Bay_01-Shelf_1");
        insertShelfLocation(warehouseB, "Aisle_B-Bay_01-Shelf_1");

        User picker = createUser("picker@iso.test", "Iso Picker", "HUB_PICKER");
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                picker.getId(), warehouseA);

        createUser("admin@iso.test", "Iso Admin", "CENTRAL_ADMIN");
    }

    @Test
    void pickerWithNoFilterSeesOnlyAssignedWarehouse() throws Exception {
        String token = login("picker@iso.test");

        MvcResult result = mockMvc.perform(get("/shelf-locations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<Long> warehouseIds = warehouseIdsFrom(result);
        assertThat(warehouseIds).containsExactly(warehouseA);
        assertThat(warehouseIds).doesNotContain(warehouseB);
    }

    @Test
    void pickerPassingForeignWarehouseIdIsRejected() throws Exception {
        String token = login("picker@iso.test");

        // The client tries to widen its scope by supplying warehouse B's id directly.
        mockMvc.perform(get("/shelf-locations")
                        .param("warehouseId", String.valueOf(warehouseB))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void pickerPassingOwnWarehouseIdIsHonored() throws Exception {
        String token = login("picker@iso.test");

        MvcResult result = mockMvc.perform(get("/shelf-locations")
                        .param("warehouseId", String.valueOf(warehouseA))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(warehouseIdsFrom(result)).containsExactly(warehouseA);
    }

    @Test
    void centralAdminIsNotScopedAndCanReadAnyWarehouse() throws Exception {
        String token = login("admin@iso.test");

        // No filter: admin sees both warehouses.
        MvcResult all = mockMvc.perform(get("/shelf-locations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(warehouseIdsFrom(all)).contains(warehouseA, warehouseB);

        // Filtering by warehouse B is honored for an admin — proving the picker's 403 is a
        // per-claim isolation rule, not a blanket block on that warehouse.
        MvcResult filtered = mockMvc.perform(get("/shelf-locations")
                        .param("warehouseId", String.valueOf(warehouseB))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(warehouseIdsFrom(filtered)).containsExactly(warehouseB);
    }

    // --- helpers -----------------------------------------------------------------------

    private List<Long> warehouseIdsFrom(MvcResult result) throws Exception {
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("warehouseId").asLong()));
        return ids;
    }

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD));
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private Long insertWarehouse(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES (?, ?, 'addr') RETURNING id",
                Long.class, code, name);
    }

    private void insertShelfLocation(Long warehouseId, String locationCode) {
        jdbcTemplate.update(
                "INSERT INTO shelf_locations (warehouse_id, sku, aisle, bay, shelf, location_code) "
                        + "VALUES (?, ?, 'A', '01', '1', ?)",
                warehouseId, SKU, locationCode);
    }

    private User createUser(String email, String name, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Seed role missing: " + roleName));
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
