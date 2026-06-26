package com.ashu.shelflife.orders.ingest;

/**
 * One parsed data row from an upload, before validation. All values are raw strings exactly
 * as read from the file (trimmed). {@code rowNumber} is the 1-based file line.
 */
public record RawOrderRow(
        int rowNumber,
        String orderId,
        String customerId,
        String warehouseId,
        String itemSku,
        String itemName,
        String quantityOrdered) {
}
