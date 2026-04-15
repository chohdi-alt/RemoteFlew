package tn.pi.remoteflowapplication.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.EmailNotificationService;
import tn.pi.remoteflowapplication.application.service.SmtpConfigurationService;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestApprovedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestRejectedEvent;
import tn.pi.remoteflowapplication.domain.event.TeleworkRequestSubmittedEvent;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationServiceImpl.class);
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 1000L;

    private final UserRepository userRepository;
    private final SmtpConfigurationService smtpConfigurationService;
    private final List<String> managerEmails;

    public EmailNotificationServiceImpl(
            UserRepository userRepository,
            SmtpConfigurationService smtpConfigurationService,
            @Value("${notification.manager.emails:}") String managerEmails
    ) {
        this.userRepository = userRepository;
        this.smtpConfigurationService = smtpConfigurationService;
        this.managerEmails = parseEmails(managerEmails);
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

    @Override
    public void sendAccountActivationEmail(String email, String username, String activationLink) {
        if (email == null || email.isBlank()) {
            log.warn("Activation email skipped (email unavailable) for username={}", username);
            return;
        }

        String safeUsername = username == null || username.isBlank() ? "user" : username;
        String subject = "Activate your RemoteFlow account";
        String body = "Hello " + safeUsername + ",\n\n"
                + "Your account was created. Set your password and activate your account using this link:\n"
                + activationLink + "\n\n"
                + "If you did not expect this email, please contact your administrator.";

        sendEmail(email, subject, body);
    }

    @Override
    public void notifyManagerScoringReminder(String managerEmail, Long requestId) {
        if (managerEmail == null || managerEmail.isBlank()) {
            return;
        }
        String requestRef = requestId == null ? "N/A" : requestId.toString();
        String subject = "Telework scoring pending";
        String body = "A telework request has reached its end date and is waiting for your scoring input."
                + " Request ID: " + requestRef + ".";
        sendEmail(managerEmail, subject, body);
    }

    @Override
    public void notifyHrOfNewScore(String hrEmail, Long requestId, String managerExternalId, BigDecimal totalScore) {
        if (hrEmail == null || hrEmail.isBlank()) {
            return;
        }
        String requestRef = requestId == null ? "N/A" : requestId.toString();
        String managerRef = managerExternalId == null || managerExternalId.isBlank() ? "N/A" : managerExternalId;
        String totalRef = totalScore == null ? "N/A" : totalScore.toPlainString();
        String subject = "New telework score submitted";
        String body = "A telework score is ready for HR review."
                + " Request ID: " + requestRef
                + ", manager: " + managerRef
                + ", total score: " + totalRef + ".";
        sendEmail(hrEmail, subject, body);
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
        SmtpConfigurationService.SmtpConnectionSettings settings = smtpConfigurationService.getEffectiveSettings();
        String from = resolveFromAddress(settings);
        if (from == null || from.isBlank()) {
            log.warn("Email not sent: from address is not configured. Recipient={}", to);
            return;
        }

        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                sendSingleEmail(settings, from, to, subject, body);
                log.info("Email sent to {} | {} (attempt {}/{})", to, subject, attempt, MAX_SEND_ATTEMPTS);
                return;
            } catch (Exception ex) {
                if (attempt >= MAX_SEND_ATTEMPTS) {
                    log.error("Failed to send email to {} | {} after {} attempts", to, subject, MAX_SEND_ATTEMPTS, ex);
                    return;
                }
                log.warn("Email send attempt {}/{} failed for {} | {}. Retrying...", attempt, MAX_SEND_ATTEMPTS, to, subject, ex);
                sleepBackoff(attempt);
            }
        }
    }

    private void sendSingleEmail(
            SmtpConfigurationService.SmtpConnectionSettings settings,
            String from,
            String to,
            String subject,
            String body) {
        JavaMailSenderImpl mailSender = createMailSender(settings);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper;
        try {
            helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create SMTP message payload", ex);
        }
        mailSender.send(message);
    }

    private void sleepBackoff(int attempt) {
        long delay = RETRY_BACKOFF_MS * attempt;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private JavaMailSenderImpl createMailSender(SmtpConfigurationService.SmtpConnectionSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        if (settings.port() != null) {
            sender.setPort(settings.port());
        }
        sender.setProtocol(settings.protocol());
        if (settings.username() != null && !settings.username().isBlank()) {
            sender.setUsername(settings.username());
        }
        if (settings.password() != null && !settings.password().isBlank()) {
            sender.setPassword(settings.password());
        }

        Properties javaMailProps = sender.getJavaMailProperties();
        javaMailProps.put("mail.smtp.auth", String.valueOf(settings.authEnabled()));
        javaMailProps.put("mail.smtp.starttls.enable", String.valueOf(settings.starttlsEnabled()));
        javaMailProps.put("mail.smtp.ssl.enable", String.valueOf(settings.sslEnabled()));
        if (settings.connectionTimeoutMs() != null) {
            javaMailProps.put("mail.smtp.connectiontimeout", String.valueOf(settings.connectionTimeoutMs()));
        }
        if (settings.readTimeoutMs() != null) {
            javaMailProps.put("mail.smtp.timeout", String.valueOf(settings.readTimeoutMs()));
        }
        if (settings.writeTimeoutMs() != null) {
            javaMailProps.put("mail.smtp.writetimeout", String.valueOf(settings.writeTimeoutMs()));
        }
        return sender;
    }

    private String resolveFromAddress(SmtpConfigurationService.SmtpConnectionSettings settings) {
        if (settings.fromEmail() != null && !settings.fromEmail().isBlank()) {
            return settings.fromEmail();
        }
        if (settings.username() != null && !settings.username().isBlank()) {
            return settings.username();
        }
        return null;
    }
}
