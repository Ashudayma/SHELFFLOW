package com.ashu.shelflife.inventory;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Products (SKU master data) + Master Location Mapping (BRD 3.2): uniqueness 409, regex
 * validation 400, missing-reference 404, update semantics, and admin-only RBAC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductAndLocationMappingTest {

    private static final String ADMIN_PASSWORD = "Admin-pass1";
    private static final String PICKER_PASSWORD = "Picker-pass1";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("admin@inv.test", "Inv Admin", "CENTRAL_ADMIN", ADMIN_PASSWORD);
        adminToken = login("admin@inv.test", ADMIN_PASSWORD);
    }

    @Test
    void adminCanCreateAndListProductsAndDuplicateSkuIs409() throws Exception {
        postJson("/products", """
                {"sku":"SKU-1","name":"Widget","barcode":"BC-1","unit":"EA"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-1"));

        mockMvc.perform(get("/products").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'SKU-1')]").exists());

        // Duplicate sku -> 409 from the service layer.
        postJson("/products", """
                {"sku":"SKU-1","name":"Other","unit":"EA"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void adminCanMapSkuToLocationAndDuplicateMappingIs409() throws Exception {
        long warehouseId = createWarehouse("WH-LOC", "Loc Hub");
        createProduct("SKU-LOC");

        postJson("/shelf-location", mappingBody(warehouseId, "SKU-LOC", "Aisle_A-Bay_04-Shelf_2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aisle").value("A"))
                .andExpect(jsonPath("$.bay").value("04"))
                .andExpect(jsonPath("$.shelf").value("2"))
                .andExpect(jsonPath("$.locationCode").value("Aisle_A-Bay_04-Shelf_2"));

        // Same (warehouse, sku) again -> 409 (unique constraint enforced at service layer).
        postJson("/shelf-location", mappingBody(warehouseId, "SKU-LOC", "Aisle_B-Bay_01-Shelf_1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already mapped")));
    }

    @Test
    void malformedLocationCodeIsRejectedWith400AndDescriptiveMessage() throws Exception {
        long warehouseId = createWarehouse("WH-BAD", "Bad Hub");
        createProduct("SKU-BAD");

        postJson("/shelf-location", mappingBody(warehouseId, "SKU-BAD", "A-04-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("location_code")))
                .andExpect(jsonPath("$.message").value(containsString("Aisle_A-Bay_04-Shelf_2")));
    }

    @Test
    void missingWarehouseOrProductIs404() throws Exception {
        long warehouseId = createWarehouse("WH-404", "404 Hub");
        createProduct("SKU-404");

        // Unknown warehouse.
        postJson("/shelf-location", mappingBody(999_999L, "SKU-404", "Aisle_A-Bay_01-Shelf_1"))
                .andExpect(status().isNotFound());

        // Unknown sku.
        postJson("/shelf-location", mappingBody(warehouseId, "NOPE", "Aisle_A-Bay_01-Shelf_1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesExistingMappingAndUnknownMappingIs404() throws Exception {
        long warehouseId = createWarehouse("WH-UPD", "Upd Hub");
        createProduct("SKU-UPD");

        postJson("/shelf-location", mappingBody(warehouseId, "SKU-UPD", "Aisle_A-Bay_01-Shelf_1"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/shelf-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mappingBody(warehouseId, "SKU-UPD", "Aisle_C-Bay_12-Shelf_9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationCode").value("Aisle_C-Bay_12-Shelf_9"))
                .andExpect(jsonPath("$.bay").value("12"));

        // Updating a (warehouse, sku) with no existing mapping -> 404.
        createProduct("SKU-UNMAPPED");
        mockMvc.perform(put("/shelf-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mappingBody(warehouseId, "SKU-UNMAPPED", "Aisle_A-Bay_01-Shelf_1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void hubPickerCannotManageProductsOrLocations() throws Exception {
        long warehouseId = createWarehouse("WH-RBAC", "Rbac Hub");
        createProduct("SKU-RBAC");
        createUser("picker@inv.test", "Inv Picker", "HUB_PICKER", PICKER_PASSWORD);
        String pickerToken = login("picker@inv.test", PICKER_PASSWORD);

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + pickerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/shelf-location")
                        .header("Authorization", "Bearer " + pickerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mappingBody(warehouseId, "SKU-RBAC", "Aisle_A-Bay_01-Shelf_1")))
                .andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------

    private String mappingBody(long warehouseId, String sku, String locationCode) {
        return "{\"warehouseId\":" + warehouseId + ",\"sku\":\"" + sku
                + "\",\"locationCode\":\"" + locationCode + "\"}";
    }

    private long createWarehouse(String code, String name) throws Exception {
        MvcResult result = postJson("/warehouses",
                "{\"warehouseCode\":\"" + code + "\",\"warehouseName\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createProduct(String sku) throws Exception {
        postJson("/products", "{\"sku\":\"" + sku + "\",\"name\":\"" + sku + "\",\"unit\":\"EA\"}")
                .andExpect(status().isCreated());
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
