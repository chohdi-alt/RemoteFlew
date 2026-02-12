package tn.pi.remoteflowapplication.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestApprovedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestRejectedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestSubmittedEvent;
import tn.pi.remoteflowapplication.domain.entity.Notification;
import tn.pi.remoteflowapplication.application.port.out.NotificationRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

@Service
public class NotificationApplicationService {

        private final EmailNotificationService emailNotificationService;
        private final NotificationRepository notificationRepository;
        private final TeleworkRequestRepository teleworkRequestRepository;

        public NotificationApplicationService(
                        EmailNotificationService emailNotificationService,
                        NotificationRepository notificationRepository,
                        TeleworkRequestRepository teleworkRequestRepository) {
                this.emailNotificationService = emailNotificationService;
                this.notificationRepository = notificationRepository;
                this.teleworkRequestRepository = teleworkRequestRepository;
        }

        @EventListener
        public void onSubmitted(TeleworkRequestSubmittedEvent event) {
                var request = teleworkRequestRepository.findById(event.getRequestId())
                                .orElseThrow();
                notificationRepository.save(new Notification(
                                "Nouvelle demande soumise par " + event.getEmployeeId(),
                                "SUBMISSION",
                                request));
                emailNotificationService.notifyManagersOfSubmission(event);
        }

        @EventListener
        public void onApproved(TeleworkRequestApprovedEvent event) {
                var request = teleworkRequestRepository.findById(event.getRequestId())
                                .orElseThrow();
                notificationRepository.save(new Notification(
                                "Votre demande a été approuvée: " + event.getDecisionComment(),
                                "APPROVAL",
                                request));
                emailNotificationService.notifyEmployeeOfApproval(event);
        }

        @EventListener
        public void onRejected(TeleworkRequestRejectedEvent event) {
                var request = teleworkRequestRepository.findById(event.getRequestId())
                                .orElseThrow();
                notificationRepository.save(new Notification(
                                "Votre demande a été rejetée: " + event.getDecisionComment(),
                                "REJECTION",
                                request));
                emailNotificationService.notifyEmployeeOfRejection(event);
        }
}
