package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestSubmittedEvent;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

@Component
public class TeleworkWorkflowStarter {

    private static final Logger logger = LoggerFactory.getLogger(TeleworkWorkflowStarter.class);

    private final TeleworkRequestRepository repository;
    private final WorkflowOrchestrationPort camundaWorkflowService;

    public TeleworkWorkflowStarter(
            TeleworkRequestRepository repository,
            WorkflowOrchestrationPort camundaWorkflowService) {
        this.repository = repository;
        this.camundaWorkflowService = camundaWorkflowService;
    }

    /**
     * Starts the Camunda process asynchronously after the initial transaction has
     * committed.
     * This decouples the workflow orchestration from the business transaction.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTeleworkRequestSubmitted(TeleworkRequestSubmittedEvent event) {
        logger.info("Handling TeleworkRequestSubmittedEvent for request {}", event.getRequestId());

        repository.findById(event.getRequestId()).ifPresent(request -> {
            try {
                boolean specialCase = request.getStatus() == RequestStatus.SPECIAL;
                String processInstanceId = camundaWorkflowService.startTeleworkProcess(
                        request.getId(),
                        event.getEmployeeId(),
                        specialCase);
                request.linkProcess(processInstanceId);
                repository.save(request);
                logger.info("Successfully started workflow for request {}", request.getId());
            } catch (Exception e) {
                logger.error("Failed to start workflow for request {}: {}", request.getId(), e.getMessage());
                // In an enterprise system, we would rely on a dead-letter queue or retry
                // mechanism for missed workflow creations
            }
        });
    }
}
