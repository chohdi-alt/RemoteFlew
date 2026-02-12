
package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.pi.remoteflowapplication.application.dto.ReportRequestDTO;
import tn.pi.remoteflowapplication.application.query.ReportQueryService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportQueryService reportService;

    public ReportController(ReportQueryService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public Object generate(@RequestBody @Valid ReportRequestDTO dto) {
        return reportService.generateReport(dto);
    }
}
