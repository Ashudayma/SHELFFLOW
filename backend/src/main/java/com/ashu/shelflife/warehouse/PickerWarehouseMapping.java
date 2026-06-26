package com.ashu.shelflife.warehouse;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join row assigning a HUB_PICKER to a warehouse. Composite PK (picker_id, warehouse_id).
 */
@Entity
@Table(name = "picker_warehouse_mapping")
@Getter
@Setter
@NoArgsConstructor
public class PickerWarehouseMapping {

    @EmbeddedId
    private PickerWarehouseId id;
}
