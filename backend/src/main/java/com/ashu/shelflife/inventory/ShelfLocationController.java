package com.ashu.shelflife.inventory;

import com.ashu.shelflife.inventory.dto.ShelfLocationResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Picker-facing shelf-location lookup. Accessible to HUB_PICKER and CENTRAL_ADMIN; the
 * service layer enforces warehouse data isolation from the caller's JWT claims.
 */
@RestController
@RequestMapping("/shelf-locations")
public class ShelfLocationController {

    private final ShelfLocationService shelfLocationService;

    public ShelfLocationController(ShelfLocationService shelfLocationService) {
        this.shelfLocationService = shelfLocationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'HUB_PICKER')")
    public List<ShelfLocationResponse> list(
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return shelfLocationService.list(principal, warehouseId);
    }
}
