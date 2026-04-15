package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.AdminUserDTO;
import tn.pi.remoteflowapplication.application.dto.ArchiveSummaryDTO;
import tn.pi.remoteflowapplication.application.dto.AuditLogDTO;
import tn.pi.remoteflowapplication.application.dto.CreateUserRequest;
import tn.pi.remoteflowapplication.application.dto.RoleDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaConfigRequest;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaConfigResponse;
import tn.pi.remoteflowapplication.application.dto.UpdateUserActivationRequest;
import tn.pi.remoteflowapplication.application.dto.UpdateUserRolesRequest;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.service.AdminUserService;
import tn.pi.remoteflowapplication.application.service.AuditLogQueryService;
import tn.pi.remoteflowapplication.application.service.RoleService;
import tn.pi.remoteflowapplication.application.service.SystemConfigurationService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkScoreJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import static net.logstash.logback.argument.StructuredArguments.kv;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";

    private final AdminUserService adminUserService;
    private final AuditLogQueryService auditLogQueryService;
    private final SystemConfigurationService systemConfigurationService;
    private final RoleService roleService;
    private final TeleworkRequestRepository teleworkRequestRepository;
    private final DocumentStoragePort documentStoragePort;
    private final SpringTeleworkScoreJpaRepository teleworkScoreRepository;
    private final ClientIpResolver clientIpResolver;

    public AdminController(
            AdminUserService adminUserService,
            AuditLogQueryService auditLogQueryService,
            SystemConfigurationService systemConfigurationService,
            RoleService roleService,
            TeleworkRequestRepository teleworkRequestRepository,
            DocumentStoragePort documentStoragePort,
            SpringTeleworkScoreJpaRepository teleworkScoreRepository,
            ClientIpResolver clientIpResolver) {
        this.adminUserService = adminUserService;
        this.auditLogQueryService = auditLogQueryService;
        this.systemConfigurationService = systemConfigurationService;
        this.roleService = roleService;
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.documentStoragePort = documentStoragePort;
        this.teleworkScoreRepository = teleworkScoreRepository;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/users")
    public Page<AdminUserDTO> getUsers(Pageable pageable) {
        return adminUserService.findAll(pageable);
    }

    @PutMapping("/users/{externalId}/roles")
    public AdminUserDTO updateUserRoles(
            @PathVariable String externalId,
            @RequestBody @Valid UpdateUserRolesRequest request) {
        return adminUserService.updateRoles(externalId, request.roles());
    }

    @PutMapping("/users/{externalId}/activation")
    public AdminUserDTO updateUserActivation(
            @PathVariable String externalId,
            @RequestBody @Valid UpdateUserActivationRequest request) {
        return adminUserService.updateActivation(externalId, request.active());
    }

    @PostMapping("/users")
    public AdminUserDTO createUser(@RequestBody @Valid CreateUserRequest request) {
        return adminUserService.createUser(request);
    }

    @PostMapping("/users/sync")
    public void syncUsersFromKeycloak() {
        adminUserService.syncUsersFromKeycloak();
    }

    @GetMapping("/roles")
    public List<RoleDTO> getRoles() {
        return roleService.getRoles();
    }

    @GetMapping("/audit-logs")
    public Page<AuditLogDTO> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return auditLogQueryService.search(action, actor, from, to, pageable);
    }

    @PostMapping("/rules/reload")
    public void reloadBusinessRules() {
        // future: dynamic rules
    }

    @PostMapping("/workflow/redeploy")
    public void redeployWorkflow() {
        // future: Camunda redeploy
    }

    @PutMapping("/config/telework-quota")
    public TeleworkQuotaConfigResponse updateTeleworkQuota(
            @RequestBody @Valid TeleworkQuotaConfigRequest request,
            HttpServletRequest httpRequest) {
        int originalValue = systemConfigurationService.getTeleworkMaxDaysPerWeek();
        int maxDaysPerWeek = systemConfigurationService.updateTeleworkMaxDaysPerWeek(request.maxDaysPerWeek());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminUser = (auth != null) ? auth.getName() : "system";
        String ip = clientIpResolver.resolve(httpRequest);
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : "N/A";

        log.warn("ADMIN_EVENT",
                kv("event", "ADMIN_CONFIG_UPDATED"),
                kv("event_normalized", "admin.config.updated"),
                kv("category", "ADMIN"),
                kv("outcome", "SUCCESS"),
                kv("severity", "HIGH"),
                kv("adminUser", adminUser),
                kv("configKey", "telework.max-days-per-week"),
                kv("oldValue", originalValue),
                kv("newValue", maxDaysPerWeek),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", traceId),
                kv("source", "remoteflow-backend"),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"));

        return new TeleworkQuotaConfigResponse(maxDaysPerWeek);
    }

    @GetMapping("/archives")
    public List<ArchiveSummaryDTO> getArchivedRequests(HttpServletRequest request) {
        List<ArchiveSummaryDTO> result = teleworkRequestRepository.findArchivedRequests().stream()
                .map(this::toArchiveSummaryDTO)
                .toList();

        String ip = clientIpResolver.resolve(request);
        String traceId = getTraceId();
        String user = getCurrentUser();

        log.info("FILE_EVENT",
                kv("event", "FILE_ARCHIVE_LIST"),
                kv("event_normalized", "file.archive.list"),
                kv("category", "FILE"),
                kv("outcome", "SUCCESS"),
                kv("severity", "MEDIUM"),
                kv("user", user),
                kv("count", result.size()),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", traceId),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));

        return result;
    }

    @GetMapping("/archives/{requestId}/view")
    public ResponseEntity<byte[]> viewArchivePdf(@PathVariable Long requestId, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        String traceId = getTraceId();
        String user = getCurrentUser();
        MDC.put("user", user);

        try {
            TeleworkRequest request = teleworkRequestRepository.findById(requestId)
                    .orElseThrow(() -> new BusinessException("Request not found"));

            String archiveNodeId = normalizeNodeId(request.getArchiveNodeId());
            if (archiveNodeId == null || archiveNodeId.isBlank()) {
                throw new BusinessException("No archive found for request " + requestId);
            }

            log.warn("FILE_EVENT",
                    kv("event", "FILE_ARCHIVE_DOWNLOAD"),
                    kv("event_normalized", "file.archive.download"),
                    kv("category", "FILE"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("user", user),
                    kv("requestId", requestId),
                    kv("nodeId", archiveNodeId),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));

            byte[] pdfBytes = documentStoragePort.download(archiveNodeId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none';")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            org.springframework.http.ContentDisposition.attachment()
                                    .filename("archive_" + requestId + ".pdf").build().toString())
                    .body(pdfBytes);
        } catch (Exception ex) {
            log.error("FILE_EVENT",
                    kv("event", "FILE_ARCHIVE_DOWNLOAD_FAILED"),
                    kv("event_normalized", "file.archive.download.failed"),
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

    private ArchiveSummaryDTO toArchiveSummaryDTO(TeleworkRequest request) {
        String archiveId = normalizeNodeId(request.getArchiveNodeId());
        log.info("[API_MAPPING] id={} archiveNodeId={}", request.getId(), archiveId);
        var score = teleworkScoreRepository.findByTeleworkRequest_Id(request.getId()).orElse(null);

        boolean isSpecial = request.getStatus() == RequestStatus.SPECIAL
                || (request.getJustificationReason() != null && !request.getJustificationReason().isBlank());

        return new ArchiveSummaryDTO(
                request.getId(),
                request.getEmployeeId(),
                request.getStatus().name(),
                request.getStartDate(),
                request.getEndDate(),
                request.getManagerExternalId(),
                request.getManagerDecisionAt(),
                request.getManagerComment(),
                request.getHrExternalId(),
                request.getHrDecisionAt(),
                request.getHrComment(),
                request.getSubmittedAt(),
                isSpecial,
                request.getJustificationReason(),
                request.getAlfrescoNodeId(),
                archiveId,
                score == null ? null : score.getId(),
                score == null || score.getStatus() == null ? null : score.getStatus().name(),
                score == null ? null : score.getTotalScore());
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || "unknown".equalsIgnoreCase(ip))
            return false;
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private String normalizeNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String value = nodeId.trim();
        if (value.startsWith(WORKSPACE_PREFIX)) {
            return value.substring(WORKSPACE_PREFIX.length());
        }
        return value;
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "anonymous";
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null) ? traceId : "N/A";
    }
}
