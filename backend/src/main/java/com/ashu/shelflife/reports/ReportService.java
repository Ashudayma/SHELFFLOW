package com.ashu.shelflife.reports;

import com.ashu.shelflife.audit.AuditAction;
import com.ashu.shelflife.audit.AuditEvent;
import com.ashu.shelflife.reports.dto.ReportRow;
import com.opencsv.CSVWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BRD 3.4 Dispatch Report: builds the report rows (with dynamic fulfillment rate) and renders
 * CSV/Excel. Every report access publishes a DOWNLOAD_REPORT audit event via the shared
 * (async) audit mechanism.
 */
@Service
public class ReportService {

    static final String[] HEADERS = {
            "Order_ID", "Picker_ID", "Warehouse_ID", "Item_SKU", "Item_Name",
            "Quantity_Ordered", "Quantity_Picked", "Fulfillment_Rate"
    };

    private final DispatchReportRepository reportRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReportService(DispatchReportRepository reportRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.reportRepository = reportRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Generate the report rows for the given optional filters and audit the access.
     *
     * @param requestedByUserId the admin requesting the report (for the audit event)
     * @param date              optional order-creation date filter
     * @param warehouseCode     optional Warehouse_ID (warehouse code) filter
     * @param pickerId          optional Picker_ID filter
     */
    @Transactional(readOnly = true)
    public List<ReportRow> generate(Long requestedByUserId, LocalDate date,
                                    String warehouseCode, Long pickerId) {
        OffsetDateTime startDate = null;
        OffsetDateTime endDate = null;
        if (date != null) {
            ZoneId zone = ZoneId.systemDefault();
            startDate = date.atStartOfDay(zone).toOffsetDateTime();
            endDate = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        }

        List<ReportRow> rows = reportRepository.findReportRows(startDate, endDate, warehouseCode, pickerId);

        // Cross-cutting audit (BRD §4) — written asynchronously after this read-only tx commits.
        eventPublisher.publishEvent(AuditEvent.of(AuditAction.DOWNLOAD_REPORT, requestedByUserId));
        return rows;
    }

    public byte[] toCsv(List<ReportRow> rows) {
        StringWriter out = new StringWriter();
        try (CSVWriter writer = new CSVWriter(out)) {
            writer.writeNext(HEADERS);
            for (ReportRow r : rows) {
                writer.writeNext(new String[]{
                        r.orderId(),
                        r.pickerId() == null ? "" : String.valueOf(r.pickerId()),
                        r.warehouseId(),
                        r.itemSku(),
                        r.itemName(),
                        String.valueOf(r.quantityOrdered()),
                        String.valueOf(r.quantityPicked()),
                        formatRate(r.fulfillmentRate())
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render CSV report", e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toExcel(List<ReportRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dispatch Report");

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                header.createCell(c).setCellValue(HEADERS[c]);
            }

            int rowNum = 1;
            for (ReportRow r : rows) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.orderId());
                if (r.pickerId() != null) {
                    row.createCell(1).setCellValue(r.pickerId());
                }
                row.createCell(2).setCellValue(r.warehouseId());
                row.createCell(3).setCellValue(r.itemSku());
                row.createCell(4).setCellValue(r.itemName());
                row.createCell(5).setCellValue(r.quantityOrdered());
                row.createCell(6).setCellValue(r.quantityPicked());
                row.createCell(7).setCellValue(r.fulfillmentRate());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render Excel report", e);
        }
    }

    private static String formatRate(double rate) {
        return String.format(Locale.US, "%.4f", rate);
    }
}
