package tn.pi.remoteflowapplication.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.pi.remoteflowapplication.application.dto.AgreementFileDTO;
import tn.pi.remoteflowapplication.application.dto.PendingValidationTaskDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaResponse;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;
import tn.pi.remoteflowapplication.application.service.AgreementService;
import tn.pi.remoteflowapplication.application.service.TeleworkQuotaService;
import tn.pi.remoteflowapplication.application.service.ValidationInboxService;
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TeleworkQueryController {

    private final TeleworkStatusQueryService queryService;
    private final ValidationInboxService validationInboxService;
    private final AgreementService agreementService;
    private final TeleworkQuotaService teleworkQuotaService;
    private final ClientIpResolver clientIpResolver;
    private static final Logger logger = LoggerFactory.getLogger(TeleworkQueryController.class);

    public TeleworkQueryController(
            TeleworkStatusQueryService queryService,
            ValidationInboxService validationInboxService,
            AgreementService agreementService,
            TeleworkQuotaService teleworkQuotaService,
            ClientIpResolver clientIpResolver) {
        this.queryService = queryService;
        this.validationInboxService = validationInboxService;
        this.agreementService = agreementService;
        this.teleworkQuotaService = teleworkQuotaService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/telework/quota")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TeleworkQuotaResponse getCurrentQuota(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            Authentication auth) {
        if (date != null) {
            return teleworkQuotaService.getQuotaForDate(auth.getName(), date);
        }
        return teleworkQuotaService.getCurrentWeekQuota(auth.getName());
    }

    // Manager / HR / Admin: view request details
    @GetMapping("/telework/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN')")
    public TeleworkStatusDTO getRequestById(@PathVariable Long id, Authentication authentication) {
        return queryService.findById(id, authentication);
    }

    @GetMapping("/employee/telework")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<TeleworkStatusDTO> getMyRequests(Authentication auth) {
        return queryService.findForCurrentEmployee(auth.getName());
    }

    @GetMapping("/telework/{id}/history")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN','EMPLOYEE')")
    public List<tn.pi.remoteflowapplication.application.dto.AuditHistoryDTO> getRequestHistory(
            @PathVariable Long id,
            Authentication authentication) {
        return queryService.getRequestHistory(id, authentication);
    }

    @GetMapping("/telework/validations/pending")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN')")
    public Page<PendingValidationTaskDTO> getPendingValidations(
            Authentication authentication,
            Pageable pageable) {
        return validationInboxService.getPendingValidations(authentication, pageable);
    }

    @GetMapping("/telework/{id}/agreement/pdf")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR','ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadAgreementPdf(
            @PathVariable("id") Long requestId,
            Authentication authentication) {
        String ip = clientIpResolver.resolve(getRequest());
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        String traceId = getTraceId();

        try {
            AgreementFileDTO file = agreementService.downloadAgreementPdf(requestId, authentication);
            if (file == null || file.content() == null) {
                return ResponseEntity.notFound().build();
            }
            ByteArrayResource resource = new ByteArrayResource(file.content());

            logger.warn("FILE_EVENT",
                    kv("event", "FILE_AGREEMENT_DOWNLOAD"),
                    kv("event_normalized", "file.agreement.download"),
                    kv("category", "FILE"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("requestId", requestId),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("contentLength", file.content().length),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none';")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            org.springframework.http.ContentDisposition.attachment().filename(file.fileName()).build()
                                    .toString())
                    .contentLength(file.content().length)
                    .body(resource);
        } catch (Exception ex) {
            logger.error("FILE_EVENT",
                    kv("event", "FILE_AGREEMENT_DOWNLOAD_FAILED"),
                    kv("event_normalized", "file.agreement.download.failed"),
                    kv("category", "FILE"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("requestId", requestId),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("error", ex.getClass().getSimpleName()),
                    kv("error_message", ex.getMessage()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @GetMapping("/telework/{id}/justificatif/view")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN','EMPLOYEE')")
    public ResponseEntity<org.springframework.core.io.Resource> viewJustificatif(
            @PathVariable Long id,
            Authentication authentication) {
        String ip = clientIpResolver.resolve(getRequest());
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        String traceId = getTraceId();

        try {
            ResponseEntity<org.springframework.core.io.Resource> response = queryService.viewJustificatif(id,
                    authentication);
            if (response == null || response.getBody() == null) {
                return ResponseEntity.notFound().build();
            }

            logger.warn("FILE_EVENT",
                    kv("event", "FILE_JUSTIFICATION_VIEW"),
                    kv("event_normalized", "file.justification.view"),
                    kv("category", "FILE"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("requestId", id),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            return response;
        } catch (Exception ex) {
            logger.error("FILE_EVENT",
                    kv("event", "FILE_JUSTIFICATION_VIEW_FAILED"),
                    kv("event_normalized", "file.justification.view.failed"),
                    kv("category", "FILE"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("requestId", id),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("error", ex.getClass().getSimpleName()),
                    kv("error_message", ex.getMessage()),
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
