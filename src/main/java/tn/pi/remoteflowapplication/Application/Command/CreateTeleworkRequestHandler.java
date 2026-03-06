package tn.pi.remoteflowapplication.application.command;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

@Component
public class CreateTeleworkRequestHandler {

    private final TeleworkRequestRepository repository;
    private final DocumentStoragePort documentService;
    private final QuotaValidationRule quotaRule;
    private final WorkflowOrchestrationPort camundaWorkflowService;
    private final DomainEventPublisher domainEventPublisher;

    private final String teleworkFolderId;

    public CreateTeleworkRequestHandler(
            TeleworkRequestRepository repository,
            DocumentStoragePort documentService,
            QuotaValidationRule quotaRule,
            WorkflowOrchestrationPort camundaWorkflowService,
            DomainEventPublisher domainEventPublisher,
            @org.springframework.beans.factory.annotation.Value("${alfresco.telework-folder-id}") String teleworkFolderId) {
        this.repository = repository;
        this.documentService = documentService;
        this.quotaRule = quotaRule;
        this.camundaWorkflowService = camundaWorkflowService;
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

        String externalUserId = auth.getName(); // sub du JWT
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
        }

        // Persistance - Fix Defect 7: One single save per command.
        repository.save(request);
        domainEventPublisher.publishEvents(request);

        /*
         * Fix Defect 1: Distributed Transaction Desynchronization.
         * Advanced Zeebe ONLY after successful database commit.
         */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    camundaWorkflowService.startTeleworkProcess(
                            request.getId(),
                            externalUserId,
                            specialCase);
                } catch (Exception e) {
                    // Log error or handle retry logic here if necessary
                    // Note: We cannot throw from afterCommit to rollback
                }
            }
        });

        return request.getId();
    }
}
