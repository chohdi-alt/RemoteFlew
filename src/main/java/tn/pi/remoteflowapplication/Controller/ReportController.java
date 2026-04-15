
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
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportQueryService reportService;
    private final ReportExportService reportExportService;
    private final ClientIpResolver clientIpResolver;
    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    public ReportController(
            ReportQueryService reportService,
            ReportExportService reportExportService,
            ClientIpResolver clientIpResolver) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public Object generate(@RequestBody @Valid ReportRequestDTO dto, Authentication authentication) {
        String ip = clientIpResolver.resolve(getRequest());
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        String traceId = getTraceId();

        logger.info("FILE_EVENT",
                kv("event", "FILE_REPORT_GENERATE"),
                kv("event_normalized", "file.report.generate"),
                kv("category", "FILE"),
                kv("outcome", "ATTEMPT"),
                kv("severity", "MEDIUM"),
                kv("user", user),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", traceId),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));

        return reportService.generateReport(dto);
    }

    @PostMapping("/export")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam("format") String format,
            @RequestBody @Valid ReportRequestDTO dto,
            Authentication authentication) {
        String ip = clientIpResolver.resolve(getRequest());
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        String traceId = getTraceId();

        try {
            ExportedReportDTO exported = reportExportService.export(dto, ExportFormat.from(format));

            logger.warn("FILE_EVENT",
                    kv("event", "FILE_REPORT_EXPORT"),
                    kv("event_normalized", "file.report.export"),
                    kv("category", "FILE"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("format", format),
                    kv("contentLength", exported.content().length),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));

            StreamingResponseBody stream = outputStream -> {
                outputStream.write(exported.content());
                outputStream.flush();
            };

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.fileName() + "\"")
                    .contentType(MediaType.parseMediaType(exported.contentType()))
                    .contentLength(exported.content().length)
                    .body(stream);
        } catch (Exception ex) {
            logger.error("FILE_EVENT",
                    kv("event", "FILE_REPORT_EXPORT_FAILED"),
                    kv("category", "FILE"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("format", format),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("error", ex.getMessage()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null) ? traceId : "N/A";
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || "unknown".equalsIgnoreCase(ip))
            return false;
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }
}
