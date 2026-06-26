package com.ashu.shelflife.inventory.dto;

import com.ashu.shelflife.inventory.Product;

public record ProductResponse(
        String sku,
        String name,
        String barcode,
        String unit) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getSku(),
                product.getName(),
                product.getBarcode(),
                product.getUnit());
    }
}
