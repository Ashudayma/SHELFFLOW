package com.ashu.shelflife.picking;

import com.ashu.shelflife.picking.dto.ScanRequest;
import com.ashu.shelflife.picking.dto.ScanResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRD 3.3 Scan-to-Pick endpoints. HUB_PICKER only; the service enforces that the order is
 * the caller's own claimed order.
 */
@RestController
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** Scan an item against the current location step. */
    @PostMapping("/scan")
    @PreAuthorize("hasRole('HUB_PICKER')")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request,
                             @AuthenticationPrincipal AuthenticatedUser picker) {
        return scanService.scan(picker, request);
    }

    /** Skip the given item so the picker can move on and revisit it later. */
    @PostMapping("/orders/{id}/items/{itemId}/skip")
    @PreAuthorize("hasRole('HUB_PICKER')")
    public ScanResponse skip(@PathVariable("id") Long orderId,
                             @PathVariable("itemId") Long itemId,
                             @AuthenticationPrincipal AuthenticatedUser picker) {
        return scanService.skip(picker, orderId, itemId);
    }
}
