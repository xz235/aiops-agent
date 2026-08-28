package com.resume.aiops.web;

import com.resume.aiops.report.PdfReportService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final PdfReportService reportService;

    public ReportController(PdfReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{reportId}")
    ResponseEntity<Resource> download(@PathVariable String reportId) {
        Resource resource = reportService.load(reportId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(reportId + ".pdf").build().toString())
                .body(resource);
    }
}

