package com.ashu.shelflife.inventory;

import com.ashu.shelflife.inventory.dto.ShelfLocationRequest;
import com.ashu.shelflife.inventory.dto.ShelfLocationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Master Location Mapping maintenance (BRD 3.2). Administration only (CENTRAL_ADMIN).
 *
 * <p>Note: the picker-facing read endpoint is {@code GET /shelf-locations} (plural) in
 * {@link ShelfLocationController}; these admin write operations live at {@code /shelf-location}.
 */
@RestController
@RequestMapping("/shelf-location")
public class ShelfLocationAdminController {

    private final ShelfLocationService shelfLocationService;

    public ShelfLocationAdminController(ShelfLocationService shelfLocationService) {
        this.shelfLocationService = shelfLocationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ShelfLocationResponse create(@Valid @RequestBody ShelfLocationRequest request) {
        return shelfLocationService.create(request);
    }

    @PutMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ShelfLocationResponse update(@Valid @RequestBody ShelfLocationRequest request) {
        return shelfLocationService.update(request);
    }
}
