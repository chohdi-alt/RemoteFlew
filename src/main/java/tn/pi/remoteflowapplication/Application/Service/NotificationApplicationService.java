package tn.pi.remoteflowapplication.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.pi.remoteflowapplication.application.port.out.NotificationRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.domain.entity.Notification;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestApprovedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestRejectedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestSubmittedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestManagerApprovedEvent;
import tn.pi.remoteflowapplication.domain.event.WorkflowTaskCreatedEvent;
import tn.pi.remoteflowapplication.application.port.out.RealtimeSignalPublisher;

@Service
public class NotificationApplicationService {

    private final EmailNotificationService emailNotificationService;
    private final NotificationRepository notificationRepository;
    private final TeleworkRequestRepository teleworkRequestRepository;
    private final TaskExecutor notificationTaskExecutor;
    private final RealtimeSignalPublisher realtimeSignalPublisher;

    public NotificationApplicationService(
            EmailNotificationService emailNotificationService,
            NotificationRepository notificationRepository,
            TeleworkRequestRepository teleworkRequestRepository,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor,
            RealtimeSignalPublisher realtimeSignalPublisher) {
        this.emailNotificationService = emailNotificationService;
        this.notificationRepository = notificationRepository;
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.notificationTaskExecutor = notificationTaskExecutor;
        this.realtimeSignalPublisher = realtimeSignalPublisher;
    }

    @EventListener
    public void onSubmitted(TeleworkRequestSubmittedEvent event) {
        var request = teleworkRequestRepository.findById(event.getRequestId())
                .orElseThrow();
        notificationRepository.save(new Notification(
                "Nouvelle demande soumise par " + event.getEmployeeId(),
                "SUBMISSION",
                request));
        dispatchEmail(() -> emailNotificationService.notifyManagersOfSubmission(event));
        runAfterCommitDedupedSignal("ROLE:MANAGER:MANAGER_INBOX_CHANGED", () -> realtimeSignalPublisher.publishToRole("MANAGER", "MANAGER_INBOX_CHANGED", request.getId()));
    }

    @EventListener
    public void onApproved(TeleworkRequestApprovedEvent event) {
        var request = teleworkRequestRepository.findById(event.getRequestId())
                .orElseThrow();
        notificationRepository.save(new Notification(
                "Votre demande a ete approuvee: " + event.getDecisionComment(),
                "APPROVAL",
                request));
        dispatchEmail(() -> emailNotificationService.notifyEmployeeOfApproval(event));
        runAfterCommitDedupedSignal("USER:" + request.getEmployeeId() + ":EMPLOYEE_REQUEST_CHANGED", () -> realtimeSignalPublisher.publishToUser(request.getEmployeeId(), "EMPLOYEE_REQUEST_CHANGED", request.getId()));
    }

    @EventListener
    public void onRejected(TeleworkRequestRejectedEvent event) {
        var request = teleworkRequestRepository.findById(event.getRequestId())
                .orElseThrow();
        notificationRepository.save(new Notification(
                "Votre demande a ete rejetee: " + event.getDecisionComment(),
                "REJECTION",
                request));
        dispatchEmail(() -> emailNotificationService.notifyEmployeeOfRejection(event));
        runAfterCommitDedupedSignal("USER:" + request.getEmployeeId() + ":EMPLOYEE_REQUEST_CHANGED", () -> realtimeSignalPublisher.publishToUser(request.getEmployeeId(), "EMPLOYEE_REQUEST_CHANGED", request.getId()));
    }

    @EventListener
    public void onManagerApproved(TeleworkRequestManagerApprovedEvent event) {
        runAfterCommitDedupedSignal("USER:" + event.getEmployeeId() + ":EMPLOYEE_REQUEST_CHANGED", () -> realtimeSignalPublisher.publishToUser(event.getEmployeeId(), "EMPLOYEE_REQUEST_CHANGED", event.getRequestId()));
    }

    @EventListener
    public void onWorkflowTaskCreated(WorkflowTaskCreatedEvent event) {
        if ("MANAGER".equals(event.getTaskType())) {
            runAfterCommitDedupedSignal("ROLE:MANAGER:MANAGER_INBOX_CHANGED", () -> realtimeSignalPublisher.publishToRole("MANAGER", "MANAGER_INBOX_CHANGED", event.getRequestId()));
        } else if ("HR".equals(event.getTaskType())) {
            runAfterCommitDedupedSignal("ROLE:HR:HR_INBOX_CHANGED", () -> realtimeSignalPublisher.publishToRole("HR", "HR_INBOX_CHANGED", event.getRequestId()));
        }
    }

    private void dispatchEmail(Runnable emailTask) {
        runAfterCommit(() -> notificationTaskExecutor.execute(emailTask));
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

    private static final String SIGNAL_BUFFER_KEY = "tn.pi.remoteflowapplication.SIGNAL_BUFFER_KEY";

    private void runAfterCommitDedupedSignal(String deduplicationKey, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        @SuppressWarnings("unchecked")
        java.util.Set<String> bufferedKeys = (java.util.Set<String>) TransactionSynchronizationManager.getResource(SIGNAL_BUFFER_KEY);
        
        if (bufferedKeys == null) {
            bufferedKeys = new java.util.HashSet<>();
            TransactionSynchronizationManager.bindResource(SIGNAL_BUFFER_KEY, bufferedKeys);
            
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(SIGNAL_BUFFER_KEY);
                }
            });
        }

        if (bufferedKeys.add(deduplicationKey)) {
            runAfterCommit(action);
        }
    }
}
