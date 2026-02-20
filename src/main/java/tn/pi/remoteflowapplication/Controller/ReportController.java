
package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tn.pi.remoteflowapplication.application.dto.ExportFormat;
import tn.pi.remoteflowapplication.application.dto.ExportedReportDTO;
import tn.pi.remoteflowapplication.application.dto.ReportRequestDTO;
import tn.pi.remoteflowapplication.application.query.ReportQueryService;
import tn.pi.remoteflowapplication.application.service.ReportExportService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportQueryService reportService;
    private final ReportExportService reportExportService;

    public ReportController(
            ReportQueryService reportService,
            ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public Object generate(@RequestBody @Valid ReportRequestDTO dto) {
        return reportService.generateReport(dto);
    }

    @PostMapping("/export")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam("format") String format,
            @RequestBody @Valid ReportRequestDTO dto) {
        ExportedReportDTO exported = reportExportService.export(dto, ExportFormat.from(format));
        StreamingResponseBody stream = outputStream -> {
            outputStream.write(exported.content());
            outputStream.flush();
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.fileName() + "\"")
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .contentLength(exported.content().length)
                .body(stream);
    }
}
