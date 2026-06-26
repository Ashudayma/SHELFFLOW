package com.ashu.shelflife.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShelfLocationRepository extends JpaRepository<ShelfLocation, Long> {

    List<ShelfLocation> findByWarehouseIdInOrderById(Collection<Long> warehouseIds);

    Optional<ShelfLocation> findByWarehouseIdAndSku(Long warehouseId, String sku);

    boolean existsByWarehouseIdAndSku(Long warehouseId, String sku);

    List<ShelfLocation> findByWarehouseIdAndSkuIn(Long warehouseId, Collection<String> skus);
}
