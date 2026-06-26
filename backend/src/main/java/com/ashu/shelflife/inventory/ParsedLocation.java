package com.ashu.shelflife.inventory;

/**
 * The components extracted from a {@code location_code} (e.g. {@code Aisle_A-Bay_04-Shelf_2}).
 */
public record ParsedLocation(String aisle, String bay, String shelf) {
}
