package com.ashu.shelflife.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where a product (sku) lives within a warehouse. Warehouse-scoped picker-facing data:
 * reads must be filtered by the caller's permitted warehouse ids (see
 * {@code WarehouseScope}). Maps the {@code shelf_locations} table exactly.
 */
@Entity
@Table(name = "shelf_locations")
@Getter
@Setter
@NoArgsConstructor
public class ShelfLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "aisle", length = 32)
    private String aisle;

    @Column(name = "bay", length = 32)
    private String bay;

    @Column(name = "shelf", length = 32)
    private String shelf;

    @Column(name = "location_code", nullable = false, length = 64)
    private String locationCode;
}
