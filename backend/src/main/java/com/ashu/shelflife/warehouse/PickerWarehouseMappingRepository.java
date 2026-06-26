package com.ashu.shelflife.warehouse;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PickerWarehouseMappingRepository
        extends JpaRepository<PickerWarehouseMapping, PickerWarehouseId> {

    /**
     * The warehouse ids assigned to a picker. Used to stamp the {@code warehouseIds}
     * claim into a HUB_PICKER's access token at login time.
     */
    @Query("select m.id.warehouseId from PickerWarehouseMapping m where m.id.pickerId = :pickerId")
    List<Long> findWarehouseIdsByPickerId(@Param("pickerId") Long pickerId);

    /**
     * Remove all warehouse assignments for a picker (used when deleting the user).
     */
    @Modifying
    @Query("delete from PickerWarehouseMapping m where m.id.pickerId = :pickerId")
    void deleteByPickerId(@Param("pickerId") Long pickerId);
}
