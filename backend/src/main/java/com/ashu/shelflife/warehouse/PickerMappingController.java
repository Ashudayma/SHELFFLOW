package com.ashu.shelflife.warehouse;

import com.ashu.shelflife.warehouse.dto.MapPickerRequest;
import com.ashu.shelflife.warehouse.dto.PickerMappingResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Picker-to-Warehouse mapping endpoint (BRD 3.1). Administration only.
 */
@RestController
@RequestMapping("/map-picker")
public class PickerMappingController {

    private final PickerWarehouseService pickerWarehouseService;

    public PickerMappingController(PickerWarehouseService pickerWarehouseService) {
        this.pickerWarehouseService = pickerWarehouseService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public PickerMappingResponse mapPicker(@Valid @RequestBody MapPickerRequest request) {
        return pickerWarehouseService.assign(request.pickerId(), request.warehouseIds());
    }
}
