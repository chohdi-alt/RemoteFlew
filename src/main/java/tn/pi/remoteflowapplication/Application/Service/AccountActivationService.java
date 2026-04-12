package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.pi.remoteflowapplication.domain.entity.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class AccountActivationService {

    private static final Logger logger = LoggerFactory.getLogger(AccountActivationService.class);

    private final ActivationTokenService activationTokenService;
    private final EmailNotificationService emailNotificationService;
    private final TaskExecutor notificationTaskExecutor;
    private final String activationLinkBase;

    public AccountActivationService(
            ActivationTokenService activationTokenService,
            EmailNotificationService emailNotificationService,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor,
            @Value("${app.activation-link-base:http://localhost:4200/activate}") String activationLinkBase) {
        this.activationTokenService = activationTokenService;
        this.emailNotificationService = emailNotificationService;
        this.notificationTaskExecutor = notificationTaskExecutor;
        this.activationLinkBase = activationLinkBase;
    }

    public ActivationTokenService.IssuedActivationToken issueTokenAndDispatch(User user, String trigger) {
        String keycloakUserId = require(user.getKeycloakId(), "keycloakUserId");
        String username = require(user.getUsername(), "username");

        ActivationTokenService.IssuedActivationToken issuedToken = activationTokenService.issueToken(
                user,
                keycloakUserId,
                username,
                user.getEmail());

        dispatchActivationEmail(user, issuedToken.rawToken(), trigger);
        return issuedToken;
    }

    public void dispatchActivationEmail(User user, String rawToken, String trigger) {
        String email = require(user.getEmail(), "email");
        String username = require(user.getUsername(), "username");
        String keycloakUserId = require(user.getKeycloakId(), "keycloakUserId");
        String activationLink = buildActivationLink(rawToken);

        Runnable dispatchTask = () -> notificationTaskExecutor.execute(
                () -> emailNotificationService.sendAccountActivationEmail(email, username, activationLink));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask.run();
                }
            });
        } else {
            dispatchTask.run();
        }

        logger.info(
                "event=AUTH_ACTIVATION_LINK_DISPATCHED trigger={} username={} keycloakUserId={}",
                trigger == null || trigger.isBlank() ? "unspecified" : trigger,
                username,
                keycloakUserId);
    }

    private String buildActivationLink(String rawToken) {
        String delimiter = activationLinkBase.contains("?") ? "&" : "?";
        return activationLinkBase + delimiter + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for activation flow.");
        }
        return value.trim();
    }
}
