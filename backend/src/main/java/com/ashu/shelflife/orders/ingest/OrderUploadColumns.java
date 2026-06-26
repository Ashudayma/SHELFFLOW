package com.ashu.shelflife.orders.ingest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The required upload columns (BRD 3.2) and header validation shared by all parsers.
 * Matching is case-insensitive and whitespace-trimmed, but the header must contain
 * <em>exactly</em> these columns — no missing and no unexpected ones.
 */
public final class OrderUploadColumns {

    public static final String ORDER_ID = "Order_ID";
    public static final String CUSTOMER_ID = "Customer_ID";
    public static final String WAREHOUSE_ID = "Warehouse_ID";
    public static final String ITEM_SKU = "Item_SKU";
    public static final String ITEM_NAME = "Item_Name";
    public static final String QUANTITY_ORDERED = "Quantity_Ordered";

    public static final List<String> REQUIRED =
            List.of(ORDER_ID, CUSTOMER_ID, WAREHOUSE_ID, ITEM_SKU, ITEM_NAME, QUANTITY_ORDERED);

    private static final Map<String, String> CANONICAL_BY_LOWER = new HashMap<>();

    static {
        for (String column : REQUIRED) {
            CANONICAL_BY_LOWER.put(column.toLowerCase(Locale.ROOT), column);
        }
    }

    private OrderUploadColumns() {
    }

    /**
     * Map each required column to its zero-based position in the header row, validating that
     * the header holds exactly the required columns.
     *
     * @throws IllegalArgumentException if a required column is missing or an unexpected /
     *                                  duplicate column is present
     */
    public static Map<String, Integer> indexHeader(String[] header) {
        Map<String, Integer> canonicalToIndex = new HashMap<>();
        List<String> unexpected = new ArrayList<>();

        for (int i = 0; i < header.length; i++) {
            String raw = header[i] == null ? "" : header[i].trim();
            if (raw.isEmpty()) {
                continue; // ignore empty trailing header cells
            }
            String canonical = CANONICAL_BY_LOWER.get(raw.toLowerCase(Locale.ROOT));
            if (canonical == null) {
                unexpected.add(raw);
            } else if (canonicalToIndex.containsKey(canonical)) {
                throw new IllegalArgumentException("Duplicate column in header: " + canonical + ".");
            } else {
                canonicalToIndex.put(canonical, i);
            }
        }

        List<String> missing = REQUIRED.stream()
                .filter(column -> !canonicalToIndex.containsKey(column))
                .toList();

        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid header. Required columns exactly: " + REQUIRED + "."
                            + (missing.isEmpty() ? "" : " Missing: " + missing + ".")
                            + (unexpected.isEmpty() ? "" : " Unexpected: " + unexpected + "."));
        }
        return canonicalToIndex;
    }
}
