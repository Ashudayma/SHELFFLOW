package com.ashu.shelflife.orders.dto;

import java.util.List;

/**
 * Structured result of an order-ingestion upload: per-row outcomes plus a summary. The batch
 * never fails wholesale on a bad row — every data row appears in {@code rows}.
 */
public record OrderUploadReport(
        int totalRows,
        int successCount,
        int failedCount,
        int skippedCount,
        int ordersCreated,
        List<OrderUploadRowResult> rows) {

    public static OrderUploadReport of(int totalRows, int ordersCreated,
                                       List<OrderUploadRowResult> rows) {
        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (OrderUploadRowResult row : rows) {
            switch (row.status()) {
                case SUCCESS -> success++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }
        return new OrderUploadReport(totalRows, success, failed, skipped, ordersCreated, rows);
    }
}
