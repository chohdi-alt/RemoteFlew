package tn.pi.remoteflowapplication.application.command;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
public class CreateTeleworkRequestHandler {

    private final TeleworkRequestRepository repository;
    private final DocumentStoragePort documentService;
    private final QuotaValidationRule quotaRule;
    private final DomainEventPublisher domainEventPublisher;

    private final String teleworkFolderId;

    public CreateTeleworkRequestHandler(
            TeleworkRequestRepository repository,
            DocumentStoragePort documentService,
            QuotaValidationRule quotaRule,
            DomainEventPublisher domainEventPublisher,
            @org.springframework.beans.factory.annotation.Value("${alfresco.telework-folder-id}") String teleworkFolderId) {
        this.repository = repository;
        this.documentService = documentService;
        this.quotaRule = quotaRule;
        this.domainEventPublisher = domainEventPublisher;
        this.teleworkFolderId = teleworkFolderId;
    }

    @Transactional
    public Long handle(CreateTeleworkDTO dto, MultipartFile justificatif)
            throws IOException {

        // Utilisateur authentifie (Keycloak)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("Utilisateur non authentifie");
        }

        String externalUserId = auth.getName(); // preferred_username du JWT
        if (dto.getEmployeeId() != null && !dto.getEmployeeId().equals(externalUserId)) {
            throw new BusinessException("Vous ne pouvez soumettre que vos propres demandes");
        }

        // Creation de la demande (SUBMITTED)
        TeleworkRequest request = TeleworkRequest.create(
                externalUserId,
                dto.getStartDate(),
                dto.getEndDate());
        // Validation metier (quota)
        boolean hasJustificatif = justificatif != null && !justificatif.isEmpty();

        // Validation quota
        quotaRule.validate(request, hasJustificatif);

        // Cas special ?
        boolean specialCase = quotaRule.isSpecialCase(request);

        if (specialCase) {
            request.markAsSpecial();
        }

        // Upload justificatif if present
        if (hasJustificatif) {
            String nodeId = documentService.upload(justificatif, teleworkFolderId);
            request.attachJustificatif(nodeId);
        } // Persistance - Fix Defect 7: One single save per command.
        repository.save(request);
        domainEventPublisher.publishEvents(request);

        return request.getId();
    }
}
