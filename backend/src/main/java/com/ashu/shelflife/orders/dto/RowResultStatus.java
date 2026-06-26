package com.ashu.shelflife.orders.dto;

/**
 * Per-row outcome of an order upload.
 *
 * <ul>
 *   <li>{@code SUCCESS} — the row was ingested as an order item.</li>
 *   <li>{@code FAILED} — the row itself failed validation (see {@code error}).</li>
 *   <li>{@code SKIPPED} — the row is individually valid but its order was rejected because a
 *       sibling row (same Order_ID) failed.</li>
 * </ul>
 */
public enum RowResultStatus {
    SUCCESS,
    FAILED,
    SKIPPED
}
