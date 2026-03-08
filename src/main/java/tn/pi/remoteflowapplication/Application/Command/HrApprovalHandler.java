package tn.pi.remoteflowapplication.application.command;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Component
public class HrApprovalHandler {

    private final TeleworkRequestRepository repository;
    private final WorkflowOrchestrationPort camundaWorkflowService;
    private final DocumentStoragePort documentService;
    private final DomainEventPublisher domainEventPublisher;

    public HrApprovalHandler(
            TeleworkRequestRepository repository,
            WorkflowOrchestrationPort camundaWorkflowService,
            DomainEventPublisher domainEventPublisher,
            DocumentStoragePort documentService) {
        this.repository = repository;
        this.camundaWorkflowService = camundaWorkflowService;
        this.domainEventPublisher = domainEventPublisher;
        this.documentService = documentService;
    }

    @Transactional
    public void approve(Long requestId, String taskKey, ApprovalDecisionDTO dto) {
        Authentication auth = requireAuthenticatedWithRole("ROLE_HR");
        var request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (request.getStatus() != RequestStatus.SPECIAL) {
            throw new BusinessException("Request not ready for HR approval");
        }

        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            throw new BusinessException("Cannot approve a request before the workflow process has started.");
        }

        // Validate taskKey correlation before mutating state or moving documents.
        camundaWorkflowService.validateTaskKeyForRequest(
                taskKey,
                request.getProcessInstanceId(),
                requestId,
                "ROLE_HR");
        request.approve(dto.getComment());
        request.recordHrDecision(auth.getName(), Instant.now());
        request.recordApprovedAt(Instant.now());

        repository.save(request);
        domainEventPublisher.publishEvents(request);

        /* Fix Defect 1: Side effects only after successful DB commit. */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (request.getAlfrescoNodeId() != null) {
                    documentService.moveToApproved(request.getAlfrescoNodeId());
                }
                camundaWorkflowService.completeTask(taskKey, "APPROVE", dto.getComment(), auth.getName());
            }
        });
    }

    @Transactional
    public void reject(Long requestId, String taskKey, ApprovalDecisionDTO dto) {
        Authentication auth = requireAuthenticatedWithRole("ROLE_HR");
        var request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (request.getStatus() != RequestStatus.SPECIAL) {
            throw new BusinessException("Request not ready for HR approval");
        }

        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            throw new BusinessException("Cannot reject a request before the workflow process has started.");
        }

        // Validate taskKey correlation before mutating state or moving documents.
        camundaWorkflowService.validateTaskKeyForRequest(
                taskKey,
                request.getProcessInstanceId(),
                requestId,
                "ROLE_HR");
        request.reject(dto.getComment());
        request.recordHrDecision(auth.getName(), Instant.now());
        request.recordRejectedAt(Instant.now());

        repository.save(request);
        domainEventPublisher.publishEvents(request);

        /* Fix Defect 1: Side effects only after successful DB commit. */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (request.getAlfrescoNodeId() != null) {
                    documentService.moveToRejected(request.getAlfrescoNodeId());
                }
                camundaWorkflowService.completeTask(taskKey, "REJECT", dto.getComment(), auth.getName());
            }
        });
    }

    private Authentication requireAuthenticatedWithRole(String requiredRole) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("Utilisateur non authentifiÃƒÂ©");
        }
        boolean hasRole = auth.getAuthorities()
                .stream()
                .anyMatch(a -> requiredRole.equals(a.getAuthority()));
        if (!hasRole) {
            throw new BusinessException("AccÃ¨s refusÃ©: rÃ´le requis " + requiredRole);
        }
        return auth;
    }
}
