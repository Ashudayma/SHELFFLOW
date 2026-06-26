package com.ashu.shelflife.picking;

import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.picking.dto.ActiveWarehouseResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.warehouse.Warehouse;
import com.ashu.shelflife.warehouse.WarehouseRepository;
import com.ashu.shelflife.warehouse.WarehouseScope;
import com.ashu.shelflife.warehouse.dto.WarehouseResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.3 Warehouse Entry: registers and resolves a picker's active warehouse for the session.
 */
@Service
public class ActiveWarehouseService {

    private final PickerActiveWarehouseRepository activeWarehouseRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseScope warehouseScope;

    public ActiveWarehouseService(PickerActiveWarehouseRepository activeWarehouseRepository,
                                  WarehouseRepository warehouseRepository,
                                  WarehouseScope warehouseScope) {
        this.activeWarehouseRepository = activeWarehouseRepository;
        this.warehouseRepository = warehouseRepository;
        this.warehouseScope = warehouseScope;
    }

    /**
     * The warehouses this picker is mapped to (from their JWT claims), with code/name so the
     * Warehouse Entry screen can present a usable choice rather than raw ids.
     */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> mappedWarehouses(AuthenticatedUser picker) {
        if (picker.warehouseIds().isEmpty()) {
            return List.of();
        }
        return warehouseRepository.findAllById(picker.warehouseIds()).stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    /**
     * Register the active warehouse. Must be one of the picker's mapped warehouses, otherwise
     * rejected (403 via {@link WarehouseScope#assertAccessible}).
     */
    @Transactional
    public ActiveWarehouseResponse select(AuthenticatedUser picker, Long warehouseId) {
        warehouseScope.assertAccessible(picker, warehouseId);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));

        PickerActiveWarehouse active = activeWarehouseRepository.findById(picker.id())
                .orElseGet(PickerActiveWarehouse::new);
        active.setPickerId(picker.id());
        active.setWarehouseId(warehouseId);
        active.setSelectedAt(OffsetDateTime.now());
        activeWarehouseRepository.save(active);

        return new ActiveWarehouseResponse(
                warehouse.getId(), warehouse.getWarehouseCode(),
                warehouse.getWarehouseName(), active.getSelectedAt());
    }

    /**
     * The picker's active warehouse id, re-validated against their current mapped warehouses.
     *
     * @throws ConflictException if no warehouse has been selected this session
     */
    @Transactional(readOnly = true)
    public Long requireActiveWarehouseId(AuthenticatedUser picker) {
        Long warehouseId = activeWarehouseRepository.findById(picker.id())
                .map(PickerActiveWarehouse::getWarehouseId)
                .orElseThrow(() -> new ConflictException(
                        "No active warehouse selected. POST /picker/select-warehouse first."));
        // Guard against a mapping being revoked after selection.
        warehouseScope.assertAccessible(picker, warehouseId);
        return warehouseId;
    }
}
