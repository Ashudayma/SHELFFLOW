package com.ashu.shelflife.orders.ingest;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * CSV order-file parser (OpenCSV).
 */
@Component
public class CsvOrderFileParser implements OrderFileParser {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public List<RawOrderRow> parse(byte[] content) {
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {

            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                throw new IllegalArgumentException("The file is empty (no header row).");
            }

            Map<String, Integer> index = OrderUploadColumns.indexHeader(allRows.get(0));
            List<RawOrderRow> rows = new ArrayList<>();
            for (int i = 1; i < allRows.size(); i++) {
                String[] cells = allRows.get(i);
                if (isBlankRow(cells)) {
                    continue;
                }
                int fileLine = i + 1; // header is line 1
                rows.add(new RawOrderRow(
                        fileLine,
                        cell(cells, index.get(OrderUploadColumns.ORDER_ID)),
                        cell(cells, index.get(OrderUploadColumns.CUSTOMER_ID)),
                        cell(cells, index.get(OrderUploadColumns.WAREHOUSE_ID)),
                        cell(cells, index.get(OrderUploadColumns.ITEM_SKU)),
                        cell(cells, index.get(OrderUploadColumns.ITEM_NAME)),
                        cell(cells, index.get(OrderUploadColumns.QUANTITY_ORDERED))));
            }
            return rows;
        } catch (IOException | CsvException e) {
            throw new IllegalArgumentException("Failed to read CSV file: " + e.getMessage(), e);
        }
    }

    private static boolean isBlankRow(String[] cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(String[] cells, int index) {
        if (index < 0 || index >= cells.length || cells[index] == null) {
            return "";
        }
        return cells[index].trim();
    }
}
