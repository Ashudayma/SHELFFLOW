package com.ashu.shelflife.reports.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One line of the BRD 3.4 Dispatch Report. JSON keys are exactly the BRD field names. The
 * 7-arg constructor (used by the JPQL projection) computes {@code Fulfillment_Rate}.
 */
public record ReportRow(
        @JsonProperty("Order_ID") String orderId,
        @JsonProperty("Picker_ID") Long pickerId,
        @JsonProperty("Warehouse_ID") String warehouseId,
        @JsonProperty("Item_SKU") String itemSku,
        @JsonProperty("Item_Name") String itemName,
        @JsonProperty("Quantity_Ordered") Integer quantityOrdered,
        @JsonProperty("Quantity_Picked") Integer quantityPicked,
        @JsonProperty("Fulfillment_Rate") double fulfillmentRate) {

    public ReportRow(String orderId, Long pickerId, String warehouseId, String itemSku,
                     String itemName, Integer quantityOrdered, Integer quantityPicked) {
        this(orderId, pickerId, warehouseId, itemSku, itemName, quantityOrdered, quantityPicked,
                fulfillmentRate(quantityPicked, quantityOrdered));
    }

    /**
     * Quantity_Picked / Quantity_Ordered. Defensive against a zero/null ordered quantity
     * (which upload validation should already prevent): returns 0.0 rather than dividing.
     */
    public static double fulfillmentRate(Integer picked, Integer ordered) {
        if (ordered == null || ordered <= 0) {
            return 0.0;
        }
        int p = picked == null ? 0 : picked;
        return (double) p / ordered;
    }
}
