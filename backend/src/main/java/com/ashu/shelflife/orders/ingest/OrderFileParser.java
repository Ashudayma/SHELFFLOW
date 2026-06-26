package com.ashu.shelflife.orders.ingest;

import java.util.List;

/**
 * Parses an uploaded order file into raw rows. Implementations validate the header and throw
 * {@link IllegalArgumentException} for file-level problems (bad header, empty file, unreadable
 * content) — these become a 400, distinct from per-row validation failures.
 */
public interface OrderFileParser {

    /** Whether this parser handles the given upload filename (by extension). */
    boolean supports(String filename);

    /** Parse the file content into data rows (header excluded, blank rows skipped). */
    List<RawOrderRow> parse(byte[] content);
}
