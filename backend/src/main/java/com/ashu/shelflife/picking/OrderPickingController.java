package com.ashu.shelflife.picking;

import com.ashu.shelflife.picking.dto.ClaimResponse;
import com.ashu.shelflife.picking.dto.RouteResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order-scoped picker actions: claim (BRD 3.3 Order Selection) and route (BRD 3.2 Routing).
 */
@RestController
@RequestMapping("/orders")
public class OrderPickingController {

    private final OrderClaimService orderClaimService;
    private final RoutingService routingService;

    public OrderPickingController(OrderClaimService orderClaimService, RoutingService routingService) {
        this.orderClaimService = orderClaimService;
        this.routingService = routingService;
    }

    /** Claim a PENDING order, locking it from other pickers. 409 if already claimed. */
    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('HUB_PICKER')")
    public ClaimResponse claim(@PathVariable Long id,
                               @AuthenticationPrincipal AuthenticatedUser picker) {
        return orderClaimService.claim(id, picker);
    }

    /** The optimized pick route for an order, sorted by shelf location_code. */
    @GetMapping("/{id}/route")
    @PreAuthorize("hasAnyRole('CENTRAL_ADMIN', 'HUB_PICKER')")
    public RouteResponse route(@PathVariable Long id,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return routingService.route(id, caller);
    }
}
