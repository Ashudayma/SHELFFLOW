package com.ashu.shelflife.orders.ingest;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Selects the right {@link OrderFileParser} for an uploaded file by its name/extension.
 */
@Component
public class OrderFileParserFactory {

    private final List<OrderFileParser> parsers;

    public OrderFileParserFactory(List<OrderFileParser> parsers) {
        this.parsers = parsers;
    }

    public OrderFileParser forFile(String filename) {
        return parsers.stream()
                .filter(parser -> parser.supports(filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported file type for '" + filename
                                + "'. Please upload a .csv, .xls or .xlsx file."));
    }
}
