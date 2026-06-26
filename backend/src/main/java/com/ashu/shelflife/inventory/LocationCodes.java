package com.ashu.shelflife.inventory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validation and parsing for {@code location_code} values used in the BRD 3.2 Master
 * Location Mapping. The canonical format is {@code Aisle_<aisle>-Bay_<bay>-Shelf_<shelf>},
 * e.g. {@code Aisle_A-Bay_04-Shelf_2}: an alphanumeric aisle, a numeric bay, a numeric shelf.
 */
public final class LocationCodes {

    public static final String FORMAT_EXAMPLE = "Aisle_A-Bay_04-Shelf_2";

    private static final Pattern PATTERN =
            Pattern.compile("^Aisle_([A-Za-z0-9]+)-Bay_([0-9]+)-Shelf_([0-9]+)$");

    private LocationCodes() {
    }

    /**
     * Validate and parse a location code into its components.
     *
     * @throws IllegalArgumentException with a descriptive message if the value is blank or
     *                                  does not match the required format
     */
    public static ParsedLocation parse(String locationCode) {
        if (locationCode == null || locationCode.isBlank()) {
            throw new IllegalArgumentException(
                    "location_code must not be blank; expected format '" + FORMAT_EXAMPLE + "'.");
        }
        Matcher matcher = PATTERN.matcher(locationCode.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid location_code '" + locationCode + "': expected format "
                            + "'Aisle_<aisle>-Bay_<bay>-Shelf_<shelf>' with an alphanumeric aisle and "
                            + "numeric bay/shelf (e.g. '" + FORMAT_EXAMPLE + "').");
        }
        return new ParsedLocation(matcher.group(1), matcher.group(2), matcher.group(3));
    }
}
