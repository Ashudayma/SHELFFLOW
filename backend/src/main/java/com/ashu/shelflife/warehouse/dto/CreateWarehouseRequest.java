package com.ashu.shelflife.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank(message = "must not be blank") @Size(max = 50) String warehouseCode,
        @NotBlank(message = "must not be blank") @Size(max = 150) String warehouseName,
        @Size(max = 500) String address) {
}
