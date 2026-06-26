package com.ashu.shelflife.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank(message = "must not be blank") @Size(max = 64) String sku,
        @NotBlank(message = "must not be blank") @Size(max = 255) String name,
        @Size(max = 128) String barcode,
        @Size(max = 32) String unit) {
}
