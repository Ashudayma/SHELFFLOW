package com.ashu.shelflife.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ashu.shelflife.auth.dto.LoginRequest;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the admin-only management endpoints end-to-end: warehouse CRUD, user CRUD,
 * picker-to-warehouse mapping, and RBAC on those endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminManagementTest {

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
        Role admin = roleRepository.findByName("CENTRAL_ADMIN").orElseThrow();
        User user = new User();
        user.setName("Root Admin");
        user.setEmail("root@admin.test");
        user.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        user.setRole(admin);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        adminToken = login("root@admin.test", ADMIN_PASSWORD);
    }

    @Test
    void adminCanCreateWarehouseUserAndMapThem() throws Exception {
        long warehouseId = createWarehouse("WH-MGMT", "Mgmt Hub");

        // Create a HUB_PICKER.
        long pickerId = asLong(postJson("/users", """
                {"name":"New Picker","email":"newpicker@mgmt.test","password":"Picker-pass1",
                 "role":"HUB_PICKER"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("HUB_PICKER"))
                .andReturn(), "id");

        // Map the picker to the warehouse.
        postJson("/map-picker",
                "{\"pickerId\":" + pickerId + ",\"warehouseIds\":[" + warehouseId + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickerId").value((int) pickerId))
                .andExpect(jsonPath("$.warehouseIds[0]").value((int) warehouseId));

        // List users includes the new picker.
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'newpicker@mgmt.test')]").exists());

        // Update the picker.
        mockMvc.perform(put("/users/" + pickerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Picker","email":"newpicker@mgmt.test",
                                 "role":"HUB_PICKER","status":"INACTIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Picker"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void mappingANonPickerIsRejected() throws Exception {
        // The admin account itself is not a HUB_PICKER.
        long adminId = userRepository.findByEmail("root@admin.test").orElseThrow().getId();
        long warehouseId = createWarehouse("WH-NP", "No-Picker Hub");

        postJson("/map-picker",
                "{\"pickerId\":" + adminId + ",\"warehouseIds\":[" + warehouseId + "]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void hubPickerCannotCreateUsers() throws Exception {
        // Create a picker, then act as them.
        postJson("/users", """
                {"name":"Rbac Picker","email":"rbac@mgmt.test","password":"Picker-pass1",
                 "role":"HUB_PICKER"}""")
                .andExpect(status().isCreated());
        String pickerToken = login("rbac@mgmt.test", PICKER_PASSWORD);

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + pickerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Fail","email":"fail@mgmt.test","password":"Picker-pass1",
                                 "role":"HUB_PICKER"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // --- helpers -----------------------------------------------------------------------

    private long createWarehouse(String code, String name) throws Exception {
        return asLong(postJson("/warehouses",
                "{\"warehouseCode\":\"" + code + "\",\"warehouseName\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn(), "id");
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body)
            throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long asLong(MvcResult result, String field) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get(field).asLong();
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
