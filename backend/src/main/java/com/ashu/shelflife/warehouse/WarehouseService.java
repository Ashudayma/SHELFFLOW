package com.ashu.shelflife.warehouse;

import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.warehouse.dto.CreateWarehouseRequest;
import com.ashu.shelflife.warehouse.dto.WarehouseResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listAll() {
        return warehouseRepository.findAll().stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByWarehouseCode(request.warehouseCode())) {
            throw new ConflictException("Warehouse code already exists: " + request.warehouseCode());
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseCode(request.warehouseCode());
        warehouse.setWarehouseName(request.warehouseName());
        warehouse.setAddress(request.address());
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }
}
