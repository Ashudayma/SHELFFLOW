package com.ashu.shelflife.picking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.orders.Order;
import com.ashu.shelflife.orders.OrderRepository;
import com.ashu.shelflife.orders.OrderStatus;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.users.Role;
import com.ashu.shelflife.users.RoleRepository;
import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserRepository;
import com.ashu.shelflife.users.UserStatus;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BRD 3.3 Order Selection concurrency: two pickers claim the same order simultaneously and
 * exactly one succeeds; the other gets a 409 conflict via optimistic locking on
 * {@code orders.version}.
 *
 * <p>Not {@code @Transactional}: the worker threads run their own committing transactions, so
 * this test commits and cleans up explicitly in {@link #cleanUp()}.
 */
@SpringBootTest
class OrderClaimConcurrencyTest {

    @Autowired private OrderClaimService orderClaimService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long warehouseId;
    private Long orderId;
    private Long picker1Id;
    private Long picker2Id;

    @BeforeEach
    void setUp() {
        warehouseId = jdbcTemplate.queryForObject(
                "INSERT INTO warehouses (warehouse_code, warehouse_name, address) "
                        + "VALUES ('WH-CONC', 'Concurrency Hub', 'addr') RETURNING id", Long.class);
        picker1Id = createPicker("picker1@conc.test");
        picker2Id = createPicker("picker2@conc.test");
        mapPicker(picker1Id);
        mapPicker(picker2Id);

        Order order = new Order();
        order.setOrderNumber("ORD-CONC-1");
        order.setCustomerId("C1");
        order.setWarehouseId(warehouseId);
        order.setStatus(OrderStatus.PENDING);
        orderId = orderRepository.saveAndFlush(order).getId();
    }

    @Test
    void twoSimultaneousClaimsOnlyOneSucceeds() throws Exception {
        AuthenticatedUser picker1 = picker(picker1Id, "picker1@conc.test");
        AuthenticatedUser picker2 = picker(picker2Id, "picker2@conc.test");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2); // release both threads together
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            Callable<Void> claim1 = claimTask(picker1, barrier, successes, conflicts);
            Callable<Void> claim2 = claimTask(picker2, barrier, successes, conflicts);

            Future<Void> f1 = pool.submit(claim1);
            Future<Void> f2 = pool.submit(claim2);
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        Order claimed = orderRepository.findById(orderId).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(claimed.getVersion()).isEqualTo(1);
        assertThat(claimed.getPickerId()).isIn(picker1Id, picker2Id);
    }

    private Callable<Void> claimTask(AuthenticatedUser picker, CyclicBarrier barrier,
                                     AtomicInteger successes, AtomicInteger conflicts) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                orderClaimService.claim(orderId, picker);
                successes.incrementAndGet();
            } catch (ConflictException e) {
                conflicts.incrementAndGet();
            }
            return null;
        };
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        jdbcTemplate.update("DELETE FROM picker_warehouse_mapping WHERE picker_id IN (?, ?)",
                picker1Id, picker2Id);
        jdbcTemplate.update("DELETE FROM picker_active_warehouse WHERE picker_id IN (?, ?)",
                picker1Id, picker2Id);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", picker1Id, picker2Id);
        jdbcTemplate.update("DELETE FROM warehouses WHERE id = ?", warehouseId);
    }

    private Long createPicker(String email) {
        Role role = roleRepository.findByName("HUB_PICKER").orElseThrow();
        User user = new User();
        user.setName(email);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Picker-pass1"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user).getId();
    }

    private void mapPicker(Long pickerId) {
        jdbcTemplate.update(
                "INSERT INTO picker_warehouse_mapping (picker_id, warehouse_id) VALUES (?, ?)",
                pickerId, warehouseId);
    }

    private AuthenticatedUser picker(Long id, String email) {
        return new AuthenticatedUser(id, email, "HUB_PICKER", List.of(warehouseId));
    }
}
