package com.ashu.shelflife.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end RBAC test over the real (Flyway-migrated) database, rolled back per test via
 * {@code @Transactional}. Proves a HUB_PICKER access token is rejected (403) from a
 * CENTRAL_ADMIN-only endpoint, while a CENTRAL_ADMIN token is accepted (200), and that an
 * unauthenticated request is rejected (401) — all returning the structured error body.
 */
@SpringBootTest
@Transactional
class AuthRbacIntegrationTest {

    private static final String ADMIN_ONLY_ENDPOINT = "/users";
    private static final String PASSWORD = "S3cret-pass!";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    // Security filters must be applied so @PreAuthorize / 401 / 403 are exercised.
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.web.FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;
    private Long warehouseId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();

        warehouseId = jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES ('WH-TEST', 'Test Hub', '1 Test Way') RETURNING id",
                Long.class);

        createUser("admin@shelflife.test", "Central Admin", "CENTRAL_ADMIN");
        User picker = createUser("picker@shelflife.test", "Hub Picker", "HUB_PICKER");

        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                picker.getId(), warehouseId);
    }

    @Test
    void hubPickerTokenCannotCallCentralAdminEndpoint() throws Exception {
        String pickerToken = login("picker@shelflife.test");

        mockMvc.perform(get(ADMIN_ONLY_ENDPOINT)
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value(ADMIN_ONLY_ENDPOINT));
    }

    @Test
    void centralAdminTokenCanCallCentralAdminEndpoint() throws Exception {
        String adminToken = login("admin@shelflife.test");

        mockMvc.perform(get(ADMIN_ONLY_ENDPOINT)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get(ADMIN_ONLY_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void hubPickerTokenCarriesMappedWarehouseIds() throws Exception {
        String pickerToken = login("picker@shelflife.test");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HUB_PICKER"))
                .andExpect(jsonPath("$.warehouseIds[0]").value(warehouseId));
    }

    /** Logs in and returns the access token. */
    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.ashu.shelflife.auth.dto.LoginRequest(email, PASSWORD));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
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
