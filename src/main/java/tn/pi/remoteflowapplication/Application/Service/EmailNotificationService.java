package tn.pi.remoteflowapplication.application.service;

import tn.pi.remoteflowapplication.domain.event.TeleworkRequestApprovedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestRejectedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestSubmittedEvent;

public interface EmailNotificationService {

    void notifyManagersOfSubmission(TeleworkRequestSubmittedEvent event);

    void notifyEmployeeOfApproval(TeleworkRequestApprovedEvent event);

    void notifyEmployeeOfRejection(TeleworkRequestRejectedEvent event);

    void sendAccountActivationEmail(String email, String username, String activationLink);
}
