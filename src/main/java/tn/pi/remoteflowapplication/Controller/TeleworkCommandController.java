package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.service.CurrentUserResolverService;
import tn.pi.remoteflowapplication.domain.entity.User;

@RestController
@RequestMapping("/api/telework")
public class TeleworkCommandController {

    private static final Logger logger = LoggerFactory.getLogger(TeleworkCommandController.class);

    private final CreateTeleworkRequestHandler createHandler;
    private final ManagerApprovalHandler managerApprovalHandler;
    private final HrApprovalHandler hrApprovalHandler;
    private final CurrentUserResolverService currentUserResolverService;

    public TeleworkCommandController(
            CreateTeleworkRequestHandler createHandler,
            ManagerApprovalHandler managerApprovalHandler,
            HrApprovalHandler hrApprovalHandler,
            CurrentUserResolverService currentUserResolverService) {
        this.createHandler = createHandler;
        this.managerApprovalHandler = managerApprovalHandler;
        this.hrApprovalHandler = hrApprovalHandler;
        this.currentUserResolverService = currentUserResolverService;
    }

    @PostMapping(consumes = { "multipart/form-data" })
    @PreAuthorize("hasRole('EMPLOYEE')")
    public void create(
            @RequestPart("data") @Valid CreateTeleworkDTO dto,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws java.io.IOException {
        User currentUser = currentUserResolverService.resolveCurrentUser(jwt);
        logger.info("event=TELEWORK_CREATE_ATTEMPT username={} keycloakId={} hasFile={}",
                currentUser.getUsername(),
                currentUser.getKeycloakId(),
                file != null && !file.isEmpty());

        createHandler.handle(dto, file, currentUser);
        logger.info("event=TELEWORK_CREATE_SUCCESS username={} keycloakId={}",
                currentUser.getUsername(),
                currentUser.getKeycloakId());
    }

    @PostMapping("/{id}/manager/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public void managerApprove(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        managerApprovalHandler.approve(requestId, taskKey, dto);
    }

    @PostMapping("/{id}/manager/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public void managerReject(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        managerApprovalHandler.reject(requestId, taskKey, dto);
    }

    @PostMapping("/{id}/hr/approve")
    @PreAuthorize("hasRole('HR')")
    public void hrApprove(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        hrApprovalHandler.approve(requestId, taskKey, dto);
    }

    @PostMapping("/{id}/hr/reject")
    @PreAuthorize("hasRole('HR')")
    public void hrReject(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        hrApprovalHandler.reject(requestId, taskKey, dto);
    }
}
