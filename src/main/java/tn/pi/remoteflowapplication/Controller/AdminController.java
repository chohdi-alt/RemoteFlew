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

    public AdminController(
            AdminUserService adminUserService,
            AuditLogQueryService auditLogQueryService,
            SystemConfigurationService systemConfigurationService,
            RoleService roleService,
            TeleworkRequestRepository teleworkRequestRepository,
            DocumentStoragePort documentStoragePort) {
        this.adminUserService = adminUserService;
        this.auditLogQueryService = auditLogQueryService;
        this.systemConfigurationService = systemConfigurationService;
        this.roleService = roleService;
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.documentStoragePort = documentStoragePort;
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
    public TeleworkQuotaConfigResponse updateTeleworkQuota(@RequestBody @Valid TeleworkQuotaConfigRequest request) {
        int maxDaysPerWeek = systemConfigurationService.updateTeleworkMaxDaysPerWeek(request.maxDaysPerWeek());
        return new TeleworkQuotaConfigResponse(maxDaysPerWeek);
    }

    @GetMapping("/archives")
    public List<ArchiveSummaryDTO> getArchivedRequests() {
        List<ArchiveSummaryDTO> result = teleworkRequestRepository.findArchivedRequests().stream()
                .map(this::toArchiveSummaryDTO)
                .toList();
        log.info("[AUDIT] GET /api/admin/archives -> {} record(s) returned", result.size());
        return result;
    }

    @GetMapping("/archives/{requestId}/view")
    public ResponseEntity<byte[]> viewArchivePdf(@PathVariable Long requestId) {
        TeleworkRequest request = teleworkRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        String archiveNodeId = normalizeNodeId(request.getArchiveNodeId());
        if (archiveNodeId == null || archiveNodeId.isBlank()) {
            throw new BusinessException("No archive found for request " + requestId);
        }

        log.info("[ARCHIVE_DOWNLOAD] requestId={} nodeId={}", requestId, archiveNodeId);

        byte[] pdfBytes = documentStoragePort.download(archiveNodeId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=archive_" + requestId + ".pdf")
                .body(pdfBytes);
    }

    private ArchiveSummaryDTO toArchiveSummaryDTO(TeleworkRequest request) {
        String archiveId = normalizeNodeId(request.getArchiveNodeId());
        log.info("[API_MAPPING] id={} archiveNodeId={}", request.getId(), archiveId);

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
                archiveId
        );
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
}
