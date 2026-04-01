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

@Service
public class NotificationApplicationService {

    private final EmailNotificationService emailNotificationService;
    private final NotificationRepository notificationRepository;
    private final TeleworkRequestRepository teleworkRequestRepository;
    private final TaskExecutor notificationTaskExecutor;

    public NotificationApplicationService(
            EmailNotificationService emailNotificationService,
            NotificationRepository notificationRepository,
            TeleworkRequestRepository teleworkRequestRepository,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor) {
        this.emailNotificationService = emailNotificationService;
        this.notificationRepository = notificationRepository;
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.notificationTaskExecutor = notificationTaskExecutor;
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
}
