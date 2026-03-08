
package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import tn.pi.remoteflowapplication.application.dto.AdminUserDTO;
import tn.pi.remoteflowapplication.application.dto.AuditLogDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaConfigRequest;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaConfigResponse;
import tn.pi.remoteflowapplication.application.dto.UpdateUserActivationRequest;
import tn.pi.remoteflowapplication.application.dto.UpdateUserRolesRequest;
import tn.pi.remoteflowapplication.application.service.AdminUserService;
import tn.pi.remoteflowapplication.application.service.AuditLogQueryService;
import tn.pi.remoteflowapplication.application.service.SystemConfigurationService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AuditLogQueryService auditLogQueryService;
    private final SystemConfigurationService systemConfigurationService;

    public AdminController(
            AdminUserService adminUserService,
            AuditLogQueryService auditLogQueryService,
            SystemConfigurationService systemConfigurationService) {
        this.adminUserService = adminUserService;
        this.auditLogQueryService = auditLogQueryService;
        this.systemConfigurationService = systemConfigurationService;
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

    @PostMapping("/users/sync")
    public void syncUsersFromKeycloak() {
        adminUserService.syncUsersFromKeycloak();
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
}
