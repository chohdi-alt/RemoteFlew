package tn.pi.remoteflowapplication.application.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.service.ArchiveService;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import org.springframework.context.ApplicationEventPublisher;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestManagerApprovedEvent;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class ManagerApprovalHandler {

    private static final Logger logger = LoggerFactory.getLogger(ManagerApprovalHandler.class);

    private final TeleworkRequestRepository repository;
    private final WorkflowOrchestrationPort camundaWorkflowService;
    private final WorkflowTaskService workflowTaskService;
    private final DomainEventPublisher domainEventPublisher;
    private final DocumentStoragePort documentService;
    private final ArchiveService archiveService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ManagerApprovalHandler(
            TeleworkRequestRepository repository,
            WorkflowOrchestrationPort camundaWorkflowService,
            WorkflowTaskService workflowTaskService,
            DomainEventPublisher domainEventPublisher,
            DocumentStoragePort documentService,
            ArchiveService archiveService,
            ApplicationEventPublisher applicationEventPublisher) {
        this.repository = repository;
        this.camundaWorkflowService = camundaWorkflowService;
        this.workflowTaskService = workflowTaskService;
        this.domainEventPublisher = domainEventPublisher;
        this.documentService = documentService;
        this.archiveService = archiveService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public void approve(Long requestId, String taskKey, ApprovalDecisionDTO dto) {
        Authentication auth = requireAuthenticatedWithRole("ROLE_MANAGER");
        var request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (request.getStatus() != RequestStatus.SUBMITTED && request.getStatus() != RequestStatus.SPECIAL) {
            throw new BusinessException("Request not ready for manager approval");
        }

        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            throw new BusinessException("Cannot approve a request before the workflow process has started.");
        }

        // Validate taskKey correlation (stateless).
        TaskEntity task = workflowTaskService.validateAndGetTask(
                Long.valueOf(taskKey),
                requestId,
                "MANAGER");

        // Manager approval moves the request to the HR validation stage.
        request.approveByManager(dto.getComment());

        request.recordManagerDecision(auth.getName(), null);

        repository.save(request);
        domainEventPublisher.publishEvents(request);
        applicationEventPublisher.publishEvent(new TeleworkRequestManagerApprovedEvent(request.getId(), request.getEmployeeId()));
        final Long archiveRequestId = request.getId();

        runAfterCommit(() -> {
            logger.debug("Completing manager approval after commit. taskId={} jobKey={}",
                    task.getId(), task.getJobKey());
            Map<String, Object> variables = new HashMap<>();
            variables.put("decision", "APPROVE");
            if (dto.getComment() != null) {
                variables.put("managerComment", dto.getComment());
            }
            camundaWorkflowService.completeTask(String.valueOf(task.getJobKey()), variables);
            workflowTaskService.completeTask(task.getId(), auth.getName());
        });
    }

    @Transactional
    public void reject(Long requestId, String taskKey, ApprovalDecisionDTO dto) {
        Authentication auth = requireAuthenticatedWithRole("ROLE_MANAGER");
        var request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (request.getStatus() != RequestStatus.SUBMITTED && request.getStatus() != RequestStatus.SPECIAL) {
            throw new BusinessException("Request not ready for manager approval");
        }

        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            throw new BusinessException("Cannot reject a request before the workflow process has started.");
        }

        // Validate taskKey correlation before mutating state or moving documents.
        TaskEntity task = workflowTaskService.validateAndGetTask(
                Long.valueOf(taskKey),
                requestId,
                "MANAGER");

        request.rejectByManager(dto.getComment());
        request.recordManagerDecision(auth.getName(), Instant.now());
        request.recordRejectedAt(Instant.now());

        repository.save(request);
        domainEventPublisher.publishEvents(request);
        final Long archiveRequestId = request.getId();

        runAfterCommit(() -> {
            // 1. Move justificatif to rejected folder
            if (request.getAlfrescoNodeId() != null) {
                try {
                    documentService.moveToRejected(request.getAlfrescoNodeId());
                } catch (Exception e) {
                    logger.error("❌ Justificatif move failed (non-blocking)", e);
                }
            }

            // 2. CRITICAL PATH — Complete workflow task first
            try {
                Map<String, Object> variables = new HashMap<>();
                variables.put("decision", "REJECT");
                if (dto.getComment() != null) {
                    variables.put("managerComment", dto.getComment());
                }

                // Complete Zeebe Task
                camundaWorkflowService.completeTask(String.valueOf(task.getJobKey()), variables);
                // Mark Task as Completed in DB
                workflowTaskService.completeTask(task.getId(), auth.getName());
                
                logger.info("✅ Manager rejection workflow task completed. requestId={} taskId={}", requestId, task.getId());
            } catch (Exception e) {
                logger.error("❌ CRITICAL: Workflow task completion failed", e);
                throw e; // Fail loudly here as workflow integrity is critical
            }

            // 3. NON-CRITICAL PATH — Trigger archive (Non-blocking)
            if (request.getArchiveNodeId() == null) {
                try {
                    logger.error("[ARCHIVE_TRIGGERED] requestId={}", requestId);
                    archiveService.processArchive(archiveRequestId);
                } catch (Exception e) {
                    logger.error("❌ [ARCHIVE_FAILED] (NON-BLOCKING) requestId={} | Error: {}", requestId, e.getMessage());
                }
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
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
