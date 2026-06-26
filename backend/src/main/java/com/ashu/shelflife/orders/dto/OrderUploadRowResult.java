package com.ashu.shelflife.orders.dto;

/**
 * Result for a single data row of the uploaded file.
 *
 * @param row     1-based file line number (the header is line 1, so data starts at line 2)
 * @param orderId the row's Order_ID (may be blank if missing)
 * @param status  outcome
 * @param error   failure/skip reason; {@code null} for SUCCESS
 */
public record OrderUploadRowResult(
        int row,
        String orderId,
        RowResultStatus status,
        String error) {

    public static OrderUploadRowResult success(int row, String orderId) {
        return new OrderUploadRowResult(row, orderId, RowResultStatus.SUCCESS, null);
    }

    public static OrderUploadRowResult failed(int row, String orderId, String error) {
        return new OrderUploadRowResult(row, orderId, RowResultStatus.FAILED, error);
    }

    public static OrderUploadRowResult skipped(int row, String orderId, String error) {
        return new OrderUploadRowResult(row, orderId, RowResultStatus.SKIPPED, error);
    }
}
