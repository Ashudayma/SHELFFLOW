package com.ashu.shelflife.picking;

import com.ashu.shelflife.picking.dto.ActiveWarehouseResponse;
import com.ashu.shelflife.picking.dto.DashboardResponse;
import com.ashu.shelflife.picking.dto.SelectWarehouseRequest;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.warehouse.dto.WarehouseResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRD 3.3 Hub Picker Workflow entry points. HUB_PICKER only — admins have no active warehouse.
 */
@RestController
@RequestMapping("/picker")
public class PickerController {

    private final ActiveWarehouseService activeWarehouseService;
    private final PickerDashboardService dashboardService;

    public PickerController(ActiveWarehouseService activeWarehouseService,
                           PickerDashboardService dashboardService) {
        this.activeWarehouseService = activeWarehouseService;
        this.dashboardService = dashboardService;
    }

    /** The picker's mapped warehouses (id + code + name) to choose from at Warehouse Entry. */
    @GetMapping("/warehouses")
    @PreAuthorize("hasRole('HUB_PICKER')")
    public List<WarehouseResponse> myWarehouses(@AuthenticationPrincipal AuthenticatedUser picker) {
        return activeWarehouseService.mappedWarehouses(picker);
    }

    /** Warehouse Entry: register the active warehouse for this session. */
    @PostMapping("/select-warehouse")
    @PreAuthorize("hasRole('HUB_PICKER')")
    public ActiveWarehouseResponse selectWarehouse(@Valid @RequestBody SelectWarehouseRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser picker) {
        return activeWarehouseService.select(picker, request.warehouseId());
    }

    /** Dashboard scoped to the active warehouse: available / current / completed orders. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('HUB_PICKER')")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser picker) {
        return dashboardService.dashboard(picker);
    }
}
