package com.ashu.shelflife.warehouse;

import com.ashu.shelflife.warehouse.dto.CreateWarehouseRequest;
import com.ashu.shelflife.warehouse.dto.WarehouseResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Warehouse CRUD. Administration only (CENTRAL_ADMIN, global scope).
 */
@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public List<WarehouseResponse> list() {
        return warehouseService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(request);
    }
}
