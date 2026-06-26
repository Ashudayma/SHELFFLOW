package com.ashu.shelflife.orders.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/**
 * Excel order-file parser (Apache POI). Handles both .xlsx and .xls. Reads the first sheet;
 * numeric cells are normalised to their plain string form (e.g. {@code 5.0 -> "5"}).
 */
@Component
public class ExcelOrderFileParser implements OrderFileParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    @Override
    public List<RawOrderRow> parse(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("The workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("The file is empty (no header row).");
            }

            String[] header = new String[Math.max(headerRow.getLastCellNum(), 0)];
            for (int c = 0; c < header.length; c++) {
                header[c] = cell(headerRow, c);
            }
            Map<String, Integer> index = OrderUploadColumns.indexHeader(header);

            List<RawOrderRow> rows = new ArrayList<>();
            for (int rn = sheet.getFirstRowNum() + 1; rn <= sheet.getLastRowNum(); rn++) {
                Row row = sheet.getRow(rn);
                RawOrderRow parsed = new RawOrderRow(
                        rn + 1, // 1-based file/Excel row number (header is row 1)
                        cell(row, index.get(OrderUploadColumns.ORDER_ID)),
                        cell(row, index.get(OrderUploadColumns.CUSTOMER_ID)),
                        cell(row, index.get(OrderUploadColumns.WAREHOUSE_ID)),
                        cell(row, index.get(OrderUploadColumns.ITEM_SKU)),
                        cell(row, index.get(OrderUploadColumns.ITEM_NAME)),
                        cell(row, index.get(OrderUploadColumns.QUANTITY_ORDERED)));
                if (isBlank(parsed)) {
                    continue;
                }
                rows.add(parsed);
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }
    }

    private static boolean isBlank(RawOrderRow row) {
        return row.orderId().isEmpty() && row.customerId().isEmpty() && row.warehouseId().isEmpty()
                && row.itemSku().isEmpty() && row.itemName().isEmpty() && row.quantityOrdered().isEmpty();
    }

    private static String cell(Row row, int columnIndex) {
        if (row == null) {
            return "";
        }
        Cell c = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) {
            return "";
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> numericToString(c.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(c.getBooleanCellValue());
            case FORMULA -> formulaToString(c);
            default -> "";
        };
    }

    private static String formulaToString(Cell c) {
        return switch (c.getCachedFormulaResultType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> numericToString(c.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(c.getBooleanCellValue());
            default -> "";
        };
    }

    private static String numericToString(double value) {
        if (!Double.isInfinite(value) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
