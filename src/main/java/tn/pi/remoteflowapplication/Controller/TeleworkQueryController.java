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

import java.util.List;

@RestController
@RequestMapping("/api")
public class TeleworkQueryController {

    private final TeleworkStatusQueryService queryService;
    private final ValidationInboxService validationInboxService;
    private final AgreementService agreementService;
    private final TeleworkQuotaService teleworkQuotaService;

    public TeleworkQueryController(
            TeleworkStatusQueryService queryService,
            ValidationInboxService validationInboxService,
            AgreementService agreementService,
            TeleworkQuotaService teleworkQuotaService) {
        this.queryService = queryService;
        this.validationInboxService = validationInboxService;
        this.agreementService = agreementService;
        this.teleworkQuotaService = teleworkQuotaService;
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
        AgreementFileDTO file = agreementService.downloadAgreementPdf(requestId, authentication);
        ByteArrayResource resource = new ByteArrayResource(file.content());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentLength(file.content().length)
                .body(resource);
    }

    @GetMapping("/telework/{id}/justificatif/view")
    @PreAuthorize("hasAnyRole('MANAGER','HR','ADMIN','EMPLOYEE')")
    public ResponseEntity<org.springframework.core.io.Resource> viewJustificatif(
            @PathVariable Long id,
            Authentication authentication) {
        return queryService.viewJustificatif(id, authentication);
    }
}
