package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;

@RestController
@RequestMapping("/api/telework")
public class TeleworkCommandController {

    private final CreateTeleworkRequestHandler createHandler;
    private final ManagerApprovalHandler managerApprovalHandler;
    private final HrApprovalHandler hrApprovalHandler;

    public TeleworkCommandController(
            CreateTeleworkRequestHandler createHandler,
            ManagerApprovalHandler managerApprovalHandler,
            HrApprovalHandler hrApprovalHandler) {
        this.createHandler = createHandler;
        this.managerApprovalHandler = managerApprovalHandler;
        this.hrApprovalHandler = hrApprovalHandler;
    }

    @PostMapping(consumes = { "multipart/form-data" })
    @PreAuthorize("hasRole('EMPLOYEE')")
    public void create(
            @RequestPart("data") @Valid CreateTeleworkDTO dto,
            @RequestPart("file") MultipartFile file,
            java.security.Principal principal) throws java.io.IOException {
        if (!dto.getEmployeeId().equals(principal.getName())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You cannot submit requests for others.");
        }
        createHandler.handle(dto, file);
    }

    @PostMapping("/{id}/manager/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public void managerApprove(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        if (dto.getRequestId() != null && !dto.getRequestId().equals(requestId)) {
            // Ensure path/body correlation to prevent taskKey misuse.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Request id mismatch.");
        }
        managerApprovalHandler.approve(requestId, taskKey, dto);
    }

    @PostMapping("/{id}/manager/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public void managerReject(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        if (dto.getRequestId() != null && !dto.getRequestId().equals(requestId)) {
            // Ensure path/body correlation to prevent taskKey misuse.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Request id mismatch.");
        }
        managerApprovalHandler.reject(requestId, taskKey, dto);
    }

    @PostMapping("/{id}/hr/approve")
    @PreAuthorize("hasRole('HR')")
    public void hrApprove(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        if (dto.getRequestId() != null && !dto.getRequestId().equals(requestId)) {
            // Ensure path/body correlation to prevent taskKey misuse.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Request id mismatch.");
        }
        hrApprovalHandler.approve(requestId, taskKey, dto);
    }

    @PostMapping("/{id}/hr/reject")
    @PreAuthorize("hasRole('HR')")
    public void hrReject(
            @PathVariable("id") Long requestId,
            @RequestParam("taskKey") String taskKey,
            @RequestBody @Valid ApprovalDecisionDTO dto) {
        if (dto.getRequestId() != null && !dto.getRequestId().equals(requestId)) {
            // Ensure path/body correlation to prevent taskKey misuse.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Request id mismatch.");
        }
        hrApprovalHandler.reject(requestId, taskKey, dto);
    }
}
