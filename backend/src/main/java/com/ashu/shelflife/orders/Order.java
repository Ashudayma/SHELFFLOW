package com.ashu.shelflife.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer order. {@code orderNumber} holds the external Order_ID (unique). Maps the
 * {@code orders} table (schema owned by Flyway). The {@code version} column backs JPA
 * optimistic locking, used later for concurrent picking.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    // Set when a picker claims the order (BRD 3.3 Order Selection). Null while PENDING.
    @Column(name = "picker_id")
    private Long pickerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
