package com.ashu.shelflife.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link PickerWarehouseMapping}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PickerWarehouseId implements Serializable {

    @Column(name = "picker_id", nullable = false)
    private Long pickerId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PickerWarehouseId that)) {
            return false;
        }
        return Objects.equals(pickerId, that.pickerId)
                && Objects.equals(warehouseId, that.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pickerId, warehouseId);
    }
}
