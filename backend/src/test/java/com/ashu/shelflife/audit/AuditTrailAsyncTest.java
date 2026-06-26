package com.ashu.shelflife.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderItem;
import com.ashu.shelflife.orders.OrderItemStatus;
import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.orders.OrderStatus;
import com.ashu.shelflife.security.JwtService;
import com.ashu.shelflife.users.Role;
import com.ashu.shelflife.users.RoleRepository;
import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserRepository;
import com.ashu.shelflife.users.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the BRD §4 audit trail is written asynchronously and the performance-critical
 * {@code POST /scan} response does <strong>not</strong> block on the audit commit.
 *
 * <p>A test-only {@link AuditLogWriter} gates the actual write behind a latch the test holds.
 * The scan still returns quickly with no audit row committed; only after the test releases the
 * gate does the audit row appear. Not {@code @Transactional} — the scan must really commit so
 * the {@code AFTER_COMMIT} listener fires; cleanup is explicit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailAsyncTest {

    @TestConfiguration
    static class GatedWriterConfig {
        @Bean
        @Primary
        GatedAuditLogWriter gatedAuditLogWriter(AuditLogRepository repo) {
            return new GatedAuditLogWriter(repo);
        }
    }

    /**
     * Blocks the audit write until the test opens the gate; signals when it has written.
     * Deliberately NOT {@code @Transactional} so the bean is not CGLIB-proxied (which would
     * split the latch fields the test holds from the ones {@code write()} runs on);
     * {@code repository.save} supplies its own committing transaction.
     */
    static class GatedAuditLogWriter implements AuditLogWriter {
        private final AuditLogRepository repo;
        private final CountDownLatch gate = new CountDownLatch(1);
        private final CountDownLatch written = new CountDownLatch(1);

        GatedAuditLogWriter(AuditLogRepository repo) {
            this.repo = repo;
        }

        @Override
        public void write(AuditEvent event) {
            try {
                if (!gate.await(20, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            AuditLog log = new AuditLog();
            log.setUserId(event.userId());
            log.setAction(event.action().name());
            log.setOrderId(event.orderId());
            log.setSku(event.sku());
            log.setLocation(event.location());
            log.setTimestamp(event.occurredAt());
            repo.save(log);
            written.countDown();
        }

        void openGate() {
            gate.countDown();
        }

        boolean awaitWritten(long seconds) throws InterruptedException {
            return written.await(seconds, TimeUnit.SECONDS);
        }

        long writtenCount() {
            return written.getCount();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JwtService jwtService;
    @Autowired private GatedAuditLogWriter gatedWriter;

    private Long warehouseId;
    private Long pickerId;
    private Long orderId;
    private String token;

    @BeforeEach
    void setUp() {
        warehouseId = jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES ('WH-AUD', 'Audit Hub', 'addr') RETURNING id", Long.class);
        jdbcTemplate.update(
                "INSERT INTO products (sku, name, barcode, unit) VALUES ('SKU-AUD', 'Audited', NULL, 'EA')");
        jdbcTemplate.update(
                "INSERT INTO shelf_locations (warehouse_id, sku, aisle, bay, shelf, location_code) "
                        + "VALUES (?, 'SKU-AUD', 'A', '01', '1', 'Aisle_A-Bay_01-Shelf_1')", warehouseId);

        Role role = roleRepository.findByName("HUB_PICKER").orElseThrow();
        User picker = new User();
        picker.setName("Audit Picker");
        picker.setEmail("picker@audit.test");
        picker.setPasswordHash(passwordEncoder.encode("Picker-pass1"));
        picker.setRole(role);
        picker.setStatus(UserStatus.ACTIVE);
        picker = userRepository.saveAndFlush(picker);
        pickerId = picker.getId();
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                pickerId, warehouseId);

        // Build a claimed order with one item (qty 2 → a single scan won't complete it, so
        // exactly one SCAN audit event is produced).
        Order order = new Order();
        order.setOrderNumber("ORD-AUD");
        order.setCustomerId("C-AUD");
        order.setWarehouseId(warehouseId);
        order.setPickerId(pickerId);
        order.setStatus(OrderStatus.ASSIGNED);
        OrderItem item = new OrderItem();
        item.setSku("SKU-AUD");
        item.setItemName("Audited");
        item.setOrderedQuantity(2);
        item.setPickedQuantity(0);
        item.setStatus(OrderItemStatus.PENDING);
        order.addItem(item);
        orderId = orderRepository.saveAndFlush(order).getId();

        // Token minted directly so there is no LOGIN audit event to interfere.
        token = jwtService.generateAccessToken(picker, List.of(warehouseId));
    }

    @Test
    void scanReturnsBeforeTheAuditLogCommits() throws Exception {
        long start = System.nanoTime();
        mockMvc.perform(post("/scan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"sku\":\"SKU-AUD\"}"))
                .andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 1) The scan returned fast — it did NOT wait on the gated (20s) audit write.
        assertThat(elapsedMs).isLessThan(5_000L);

        // 2) At the moment the scan returned, the audit write has not committed.
        assertThat(auditCount()).isZero();
        assertThat(gatedWriter.writtenCount()).isEqualTo(1);

        // 3) Release the gate — the audit row is written asynchronously, after the response.
        gatedWriter.openGate();
        assertThat(gatedWriter.awaitWritten(5)).isTrue();

        // The recorded row has exactly the BRD §4 fields.
        assertThat(auditCount()).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, action, order_id, sku, location, timestamp "
                        + "FROM audit_logs WHERE user_id = ?", pickerId);
        assertThat(row.get("action")).isEqualTo("SCAN");
        assertThat(((Number) row.get("order_id")).longValue()).isEqualTo(orderId);
        assertThat(row.get("sku")).isEqualTo("SKU-AUD");
        assertThat(row.get("location")).isEqualTo("Aisle_A-Bay_01-Shelf_1");
        assertThat(row.get("timestamp")).isNotNull();
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE user_id = ?", Integer.class, pickerId);
    }

    @AfterEach
    void cleanUp() {
        gatedWriter.openGate(); // ensure no async thread is left blocked
        jdbcTemplate.update("DELETE FROM audit_logs WHERE user_id = ?", pickerId);
        jdbcTemplate.update(
                "DELETE FROM pick_logs WHERE session_id IN (SELECT id FROM pick_sessions WHERE order_id = ?)",
                orderId);
        jdbcTemplate.update("DELETE FROM pick_sessions WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        jdbcTemplate.update("DELETE FROM picker_warehouse_mapping WHERE picker_id = ?", pickerId);
        jdbcTemplate.update("DELETE FROM shelf_locations WHERE warehouse_id = ?", warehouseId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", pickerId);
        jdbcTemplate.update("DELETE FROM products WHERE sku = 'SKU-AUD'");
        jdbcTemplate.update("DELETE FROM warehouses WHERE id = ?", warehouseId);
    }
}
