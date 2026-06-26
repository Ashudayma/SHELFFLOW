package com.ashu.shelflife.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Master Location Mapping request (BRD 3.2): map a {@code sku} to a {@code locationCode}
 * within a specific warehouse. {@code aisle}/{@code bay}/{@code shelf} are derived from the
 * (regex-validated) {@code locationCode}, so they are not accepted from the client.
 */
public record ShelfLocationRequest(
        @NotNull(message = "must not be null") Long warehouseId,
        @NotBlank(message = "must not be blank") String sku,
        @NotBlank(message = "must not be blank") String locationCode) {
}
