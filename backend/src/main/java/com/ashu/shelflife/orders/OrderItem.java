package com.ashu.shelflife.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single line item of an {@link Order}. Maps the {@code order_items} table.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "picked_quantity", nullable = false)
    private Integer pickedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderItemStatus status = OrderItemStatus.PENDING;
}
