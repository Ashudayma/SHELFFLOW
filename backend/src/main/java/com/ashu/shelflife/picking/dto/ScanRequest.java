package com.ashu.shelflife.picking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A single scan (BRD 3.3 Scan-to-Pick).
 *
 * @param orderId the order being picked
 * @param sku     the scanned barcode's Item_SKU
 * @param scanId  optional client idempotency key; a repeated scanId is a no-op replay
 */
public record ScanRequest(
        @NotNull(message = "must not be null") Long orderId,
        @NotBlank(message = "must not be blank") String sku,
        String scanId) {
}
