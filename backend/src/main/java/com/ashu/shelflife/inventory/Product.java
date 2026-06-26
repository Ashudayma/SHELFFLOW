package com.ashu.shelflife.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A product/SKU. {@code sku} is the natural primary key (assigned, not generated).
 * Maps the {@code products} table exactly (schema owned by Flyway).
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "barcode", length = 128)
    private String barcode;

    @Column(name = "unit", length = 32)
    private String unit;
}
