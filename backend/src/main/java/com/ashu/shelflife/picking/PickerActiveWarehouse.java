package com.ashu.shelflife.picking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The warehouse a picker has registered as active for their session (BRD 3.3 Warehouse
 * Entry). One row per picker; re-selecting overwrites it. Server-side state because
 * authentication is stateless JWT.
 */
@Entity
@Table(name = "picker_active_warehouse")
@Getter
@Setter
@NoArgsConstructor
public class PickerActiveWarehouse {

    @Id
    @Column(name = "picker_id")
    private Long pickerId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "selected_at", nullable = false)
    private OffsetDateTime selectedAt;
}
