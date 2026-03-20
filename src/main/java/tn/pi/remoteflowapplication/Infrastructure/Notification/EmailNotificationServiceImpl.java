package tn.pi.remoteflowapplication.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.EmailNotificationService;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestApprovedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestRejectedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestSubmittedEvent;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationServiceImpl.class);

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final List<String> managerEmails;
    private final String fromEmail;
    private final String mailUsername;

    public EmailNotificationServiceImpl(
            UserRepository userRepository,
            JavaMailSender mailSender,
            @Value("${notification.manager.emails:}") String managerEmails,
            @Value("${notification.from-email:}") String fromEmail,
            @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.managerEmails = parseEmails(managerEmails);
        this.fromEmail = fromEmail;
        this.mailUsername = mailUsername;
    }

    @Override
    public void notifyManagersOfSubmission(TeleworkRequestSubmittedEvent event) {
        String subject = "New telework request submitted";
        String requestRef = event.getRequestId() == null ? "N/A" : event.getRequestId().toString();
        String body = "Employee " + event.getEmployeeId()
                + " submitted a telework request from "
                + event.getStartDate() + " to " + event.getEndDate()
                + " (requestId=" + requestRef + ").";

        if (managerEmails.isEmpty()) {
            log.warn("Manager notification skipped (no configured recipients). Subject: {}", subject);
            return;
        }

        for (String email : managerEmails) {
            sendEmail(email, subject, body);
        }
    }

    @Override
    public void notifyEmployeeOfApproval(TeleworkRequestApprovedEvent event) {
        String email = resolveEmployeeEmail(event.getEmployeeId());
        String subject = "Telework request approved";
        String requestRef = event.getRequestId() == null ? "N/A" : event.getRequestId().toString();
        String body = "Your telework request (requestId=" + requestRef
                + ") has been approved."
                + formatComment(event.getDecisionComment());

        sendToEmployee(email, event.getEmployeeId(), subject, body);
    }

    @Override
    public void notifyEmployeeOfRejection(TeleworkRequestRejectedEvent event) {
        String email = resolveEmployeeEmail(event.getEmployeeId());
        String subject = "Telework request rejected";
        String requestRef = event.getRequestId() == null ? "N/A" : event.getRequestId().toString();
        String body = "Your telework request (requestId=" + requestRef
                + ") has been rejected."
                + formatComment(event.getDecisionComment());

        sendToEmployee(email, event.getEmployeeId(), subject, body);
    }

    private String resolveEmployeeEmail(String employeeId) {
        try {
            return userRepository.findByUsername(employeeId)
                    .or(() -> userRepository.findByKeycloakId(employeeId))
                    .map(tn.pi.remoteflowapplication.domain.entity.User::getEmail)
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Failed to resolve email for employeeId={}", employeeId, ex);
            return null;
        }
    }

    private void sendToEmployee(String email, String employeeId, String subject, String body) {
        if (email == null || email.isBlank()) {
            log.warn("Employee notification skipped (email unavailable) for employeeId={}", employeeId);
            return;
        }
        sendEmail(email, subject, body);
    }

    private String formatComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return "";
        }
        return " Comment: " + comment;
    }

    private List<String> parseEmails(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void sendEmail(String to, String subject, String body) {
        String from = resolveFromAddress();
        if (from == null || from.isBlank()) {
            log.warn("Email not sent: from address is not configured. Recipient={}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Email sent to {} | {}", to, subject);
        } catch (Exception ex) {
            log.error("Failed to send email to {} | {}", to, subject, ex);
        }
    }

    private String resolveFromAddress() {
        if (fromEmail != null && !fromEmail.isBlank()) {
            return fromEmail;
        }
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername;
        }
        return null;
    }
}
