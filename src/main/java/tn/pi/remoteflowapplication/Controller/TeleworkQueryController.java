package tn.pi.remoteflowapplication.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.application.query.TeleworkStatusQueryService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TeleworkQueryController {

    private final TeleworkStatusQueryService queryService;

    public TeleworkQueryController(TeleworkStatusQueryService queryService) {
        this.queryService = queryService;
    }

    // Manager / HR / Admin: view request details
    @GetMapping("/telework/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN')")
    public TeleworkStatusDTO getRequestById(@PathVariable Long id) {
        return queryService.findById(id);
    }

    @GetMapping("/employee/telework")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<TeleworkStatusDTO> getMyRequests(Authentication auth) {
        return queryService.findForCurrentEmployee(auth.getName());
    }

    @GetMapping("/telework/{id}/history")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN','EMPLOYEE')")
    public List<tn.pi.remoteflowapplication.application.dto.AuditHistoryDTO> getRequestHistory(@PathVariable Long id) {
        return queryService.getRequestHistory(id);
    }
}
