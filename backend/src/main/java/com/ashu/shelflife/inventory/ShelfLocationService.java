package com.ashu.shelflife.inventory;

import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.common.error.NotFoundException;
import com.ashu.shelflife.inventory.dto.ShelfLocationRequest;
import com.ashu.shelflife.inventory.dto.ShelfLocationResponse;
import com.ashu.shelflife.security.AuthenticatedUser;
import com.ashu.shelflife.warehouse.WarehouseRepository;
import com.ashu.shelflife.warehouse.WarehouseScope;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shelf-location access and master-data maintenance.
 *
 * <p>Reads enforce BRD 3.1 Data Isolation: a HUB_PICKER only ever sees rows for warehouses
 * in their JWT claims, regardless of any client-supplied filter.
 *
 * <p>Writes maintain the BRD 3.2 Master Location Mapping: each (warehouse, sku) is mapped to
 * exactly one {@code location_code}. The unique (warehouse_id, sku) constraint is enforced
 * here as a clear 409, and {@code location_code} format is validated before persisting.
 */
@Service
public class ShelfLocationService {

    private final ShelfLocationRepository shelfLocationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final WarehouseScope warehouseScope;

    public ShelfLocationService(ShelfLocationRepository shelfLocationRepository,
                                WarehouseRepository warehouseRepository,
                                ProductRepository productRepository,
                                WarehouseScope warehouseScope) {
        this.shelfLocationRepository = shelfLocationRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.warehouseScope = warehouseScope;
    }

    /**
     * List shelf locations. {@code requestedWarehouseId} is an optional client-supplied
     * filter — it is validated/intersected against the caller's permitted scope and can
     * never widen access. A picker requesting a warehouse outside their scope is rejected
     * (AccessDenied); a picker with no filter is silently scoped to their warehouses.
     */
    @Transactional(readOnly = true)
    public List<ShelfLocationResponse> list(AuthenticatedUser principal, Long requestedWarehouseId) {
        List<ShelfLocation> rows = warehouseScope.resolveQueryScope(principal, requestedWarehouseId)
                .map(scopedIds -> scopedIds.isEmpty()
                        ? List.<ShelfLocation>of()
                        : shelfLocationRepository.findByWarehouseIdInOrderById(scopedIds))
                .orElseGet(shelfLocationRepository::findAll); // empty => CENTRAL_ADMIN, no restriction
        return rows.stream().map(ShelfLocationResponse::from).toList();
    }

    /**
     * Create a new master location mapping for a (warehouse, sku). Rejects a duplicate
     * (warehouse_id, sku) with a clear 409 and a malformed location_code with a 400.
     */
    @Transactional
    public ShelfLocationResponse create(ShelfLocationRequest request) {
        ParsedLocation parsed = LocationCodes.parse(request.locationCode());
        validateReferences(request.warehouseId(), request.sku());

        if (shelfLocationRepository.existsByWarehouseIdAndSku(request.warehouseId(), request.sku())) {
            throw new ConflictException(
                    "A shelf location is already mapped for sku '" + request.sku()
                            + "' in warehouse " + request.warehouseId()
                            + "; use PUT /shelf-location to change it.");
        }

        ShelfLocation location = new ShelfLocation();
        location.setWarehouseId(request.warehouseId());
        location.setSku(request.sku());
        applyLocation(location, parsed, request.locationCode().trim());
        return ShelfLocationResponse.from(shelfLocationRepository.save(location));
    }

    /**
     * Update the existing master location mapping for a (warehouse, sku). 404 if no mapping
     * exists; 400 for a malformed location_code.
     */
    @Transactional
    public ShelfLocationResponse update(ShelfLocationRequest request) {
        ParsedLocation parsed = LocationCodes.parse(request.locationCode());
        validateReferences(request.warehouseId(), request.sku());

        ShelfLocation location = shelfLocationRepository
                .findByWarehouseIdAndSku(request.warehouseId(), request.sku())
                .orElseThrow(() -> new NotFoundException(
                        "No shelf location mapping for sku '" + request.sku()
                                + "' in warehouse " + request.warehouseId() + "."));

        applyLocation(location, parsed, request.locationCode().trim());
        return ShelfLocationResponse.from(shelfLocationRepository.save(location));
    }

    private void validateReferences(Long warehouseId, String sku) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new NotFoundException("Warehouse not found: " + warehouseId);
        }
        if (!productRepository.existsById(sku)) {
            throw new NotFoundException("Product (sku) not found: " + sku);
        }
    }

    private void applyLocation(ShelfLocation location, ParsedLocation parsed, String locationCode) {
        location.setAisle(parsed.aisle());
        location.setBay(parsed.bay());
        location.setShelf(parsed.shelf());
        location.setLocationCode(locationCode);
    }
}
