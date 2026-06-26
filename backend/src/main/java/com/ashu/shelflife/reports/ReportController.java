package com.ashu.shelflife.reports;

import com.ashu.shelflife.reports.dto.ReportRow;
import com.ashu.shelflife.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRD 3.4 Dispatch Report. Admin only. Optional filters: date (order-creation date),
 * warehouse (warehouse code), picker (picker id). {@code format} selects the representation:
 * {@code json} (default, for the frontend table), {@code csv}, or {@code xlsx} (download).
 */
@RestController
@RequestMapping("/report")
public class ReportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<?> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String warehouse,
            @RequestParam(required = false) Long picker,
            @RequestParam(defaultValue = "json") String format,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        List<ReportRow> rows = reportService.generate(admin.id(), date, warehouse, picker);

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> ResponseEntity.ok(rows);
            case "csv" -> download(reportService.toCsv(rows), "dispatch-report.csv", "text/csv");
            case "xlsx" -> download(reportService.toExcel(rows), "dispatch-report.xlsx", XLSX_CONTENT_TYPE);
            default -> throw new IllegalArgumentException(
                    "Unsupported format '" + format + "'. Use json, csv, or xlsx.");
        };
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }
}
