package com.ashu.shelflife.warehouse;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByWarehouseCode(String warehouseCode);

    Optional<Warehouse> findByWarehouseCode(String warehouseCode);
}
