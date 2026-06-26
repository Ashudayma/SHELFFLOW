package com.ashu.shelflife.orders;

import com.ashu.shelflife.orders.dto.OrderUploadReport;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Order ingestion (BRD 3.2). Admin uploads a CSV or Excel file; the response is a structured
 * per-row report. Administration only (CENTRAL_ADMIN, global scope).
 */
@RestController
@RequestMapping("/orders")
public class OrderUploadController {

    private final OrderIngestionService orderIngestionService;

    public OrderUploadController(OrderIngestionService orderIngestionService) {
        this.orderIngestionService = orderIngestionService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public OrderUploadReport upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded or the file is empty.");
        }
        return orderIngestionService.ingest(file.getOriginalFilename(), file.getBytes());
    }
}
