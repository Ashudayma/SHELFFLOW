package com.ashu.shelflife.warehouse;

import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.users.User;
import com.ashu.shelflife.users.UserRepository;
import com.ashu.shelflife.warehouse.dto.PickerMappingResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picker-to-Warehouse mapping (BRD 3.1). Admin-driven assignment of a HUB_PICKER to
 * warehouses; these mappings are the source of the {@code warehouseIds} JWT claim.
 */
@Service
public class PickerWarehouseService {

    private static final String ROLE_HUB_PICKER = "HUB_PICKER";

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final PickerWarehouseMappingRepository mappingRepository;

    public PickerWarehouseService(UserRepository userRepository,
                                  WarehouseRepository warehouseRepository,
                                  PickerWarehouseMappingRepository mappingRepository) {
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.mappingRepository = mappingRepository;
    }

    /**
     * Assign the picker to each of the given warehouses (idempotent — existing assignments
     * are left in place). Only HUB_PICKER accounts may be mapped.
     *
     * @return the picker's full set of assigned warehouse ids afterwards
     */
    @Transactional
    public PickerMappingResponse assign(Long pickerId, List<Long> warehouseIds) {
        User picker = userRepository.findById(pickerId)
                .orElseThrow(() -> new NotFoundException("User not found: " + pickerId));

        if (!ROLE_HUB_PICKER.equals(picker.getRole().getName())) {
            throw new IllegalArgumentException(
                    "Only HUB_PICKER accounts can be mapped to warehouses; user " + pickerId
                            + " is " + picker.getRole().getName() + ".");
        }

        for (Long warehouseId : warehouseIds) {
            if (!warehouseRepository.existsById(warehouseId)) {
                throw new NotFoundException("Warehouse not found: " + warehouseId);
            }
            PickerWarehouseId id = new PickerWarehouseId(pickerId, warehouseId);
            if (!mappingRepository.existsById(id)) {
                PickerWarehouseMapping mapping = new PickerWarehouseMapping();
                mapping.setId(id);
                mappingRepository.save(mapping);
            }
        }

        return new PickerMappingResponse(pickerId,
                mappingRepository.findWarehouseIdsByPickerId(pickerId));
    }
}
