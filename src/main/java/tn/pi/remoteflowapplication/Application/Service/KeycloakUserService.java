package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.pi.remoteflowapplication.application.dto.CreateUserRequest;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KeycloakUserService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserService.class);

    private static final Set<String> SUPPORTED_ROLES = Set.of("EMPLOYEE", "MANAGER", "HR", "ADMIN");
    private static final Map<String, String> ROLE_TO_GROUP = Map.of(
            "EMPLOYEE", "employees",
            "MANAGER", "managers",
            "HR", "hr",
            "ADMIN", "admins");
    private static final Set<String> MANAGED_GROUPS = Set.copyOf(ROLE_TO_GROUP.values());

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakUserSyncService keycloakUserSyncService;
    private final UserRepository userRepository;
    private final ActivationTokenService activationTokenService;
    private final EmailNotificationService emailNotificationService;
    private final TaskExecutor notificationTaskExecutor;
    private final String activationLinkBase;

    public KeycloakUserService(
            KeycloakAuthService keycloakAuthService,
            KeycloakUserSyncService keycloakUserSyncService,
            UserRepository userRepository,
            ActivationTokenService activationTokenService,
            EmailNotificationService emailNotificationService,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor,
            @Value("${app.activation-link-base:http://localhost:4200/activate}") String activationLinkBase) {
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakUserSyncService = keycloakUserSyncService;
        this.userRepository = userRepository;
        this.activationTokenService = activationTokenService;
        this.emailNotificationService = emailNotificationService;
        this.notificationTaskExecutor = notificationTaskExecutor;
        this.activationLinkBase = activationLinkBase;
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new BusinessException("Username is required to create a Keycloak user.");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessException("Email is required for activation-based onboarding.");
        }

        String normalizedUsername = request.username().trim();
        var existingLocalUser = userRepository.findByUsername(normalizedUsername)
                .filter(user -> user.getKeycloakId() != null && !user.getKeycloakId().isBlank());
        if (existingLocalUser.isPresent()) {
            String existingKeycloakId = existingLocalUser.get().getKeycloakId();
            if (keycloakAuthService.hasValidCredentialsState(existingKeycloakId)) {
                logger.info("event=USER_CREATE_SKIPPED_EXISTING_LOCAL username={} keycloakId={}",
                        normalizedUsername,
                        existingKeycloakId);
                return existingLocalUser.get();
            }
            throw new BusinessException(
                    "Existing Keycloak user '" + normalizedUsername
                            + "' has invalid credential state. Restore credentials instead of re-onboarding.");
        }

        var existingKeycloakUserId = keycloakAuthService.findUserIdByUsername(normalizedUsername);
        if (existingKeycloakUserId.isPresent()) {
            String keycloakUserId = existingKeycloakUserId.get();
            if (!keycloakAuthService.hasValidCredentialsState(keycloakUserId)) {
                throw new BusinessException(
                        "Existing Keycloak user '" + normalizedUsername
                                + "' has invalid credential state. Restore credentials instead of re-onboarding.");
            }
            logger.info("event=USER_CREATE_SKIPPED_EXISTING_KEYCLOAK username={} keycloakId={}",
                    normalizedUsername,
                    keycloakUserId);
            keycloakUserSyncService.synchronizeUsersAndRoles();
            return userRepository.findByKeycloakId(keycloakUserId)
                    .or(() -> userRepository.findByUsername(normalizedUsername))
                    .orElseThrow(() -> new BusinessException("Existing Keycloak user was not found in local repository: " + normalizedUsername));
        }

        String normalizedRole = resolvePrimaryRole(request.roles());
        String targetGroup = ROLE_TO_GROUP.get(normalizedRole);

        String keycloakUserId = keycloakAuthService.createUser(
                normalizedUsername,
                request.email(),
                request.firstName(),
                request.lastName());

        try {
            keycloakAuthService.setRealmRoles(keycloakUserId, Set.of(normalizedRole));
            keycloakAuthService.assignUserToManagedGroup(keycloakUserId, targetGroup, MANAGED_GROUPS);
        } catch (Exception ex) {
            logger.error(
                    "event=KEYCLOAK_USER_ASSIGNMENT_FAILED username={} userId={} role={} group={} reason={}",
                    request.username(),
                    keycloakUserId,
                    normalizedRole,
                    targetGroup,
                    ex.getMessage(),
                    ex);
            keycloakAuthService.deleteUser(keycloakUserId);
            throw new BusinessException("User creation failed while assigning role/group in Keycloak.", ex);
        }

        try {
            keycloakUserSyncService.synchronizeUsersAndRoles();

            User user = userRepository.findByKeycloakId(keycloakUserId)
                    .orElseThrow(() -> new BusinessException("User synchronization failed for: " + keycloakUserId));

            ActivationTokenService.IssuedActivationToken issuedToken = activationTokenService.issueToken(
                    user,
                    keycloakUserId,
                    user.getUsername(),
                    user.getEmail());

            String activationLink = buildActivationLink(issuedToken.rawToken());
            dispatchActivationEmailAfterCommit(user.getEmail(), user.getUsername(), activationLink);
            return user;
        } catch (Exception ex) {
            logger.error("event=USER_ONBOARDING_SETUP_FAILED userId={} reason={}", keycloakUserId, ex.getMessage(), ex);
            keycloakAuthService.deleteUser(keycloakUserId);
            throw new BusinessException("User creation failed while preparing activation.", ex);
        }
    }

    private String resolvePrimaryRole(Set<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new BusinessException("Exactly one role is required: employee, manager, hr, or admin.");
        }

        Set<String> normalizedRoles = requestedRoles.stream()
                .map(this::normalizeRole)
                .filter(role -> !role.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (normalizedRoles.size() != 1) {
            throw new BusinessException("Exactly one role is allowed when creating a user.");
        }

        String role = normalizedRoles.iterator().next();
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new BusinessException("Unsupported role '" + role
                    + "'. Allowed roles are: employee, manager, hr, admin.");
        }

        return role;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }

        String value = role.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("ROLE_") ? value.substring(5) : value;
    }

    private String buildActivationLink(String rawToken) {
        String delimiter = activationLinkBase.contains("?") ? "&" : "?";
        return activationLinkBase + delimiter + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private void dispatchActivationEmailAfterCommit(String email, String username, String activationLink) {
        Runnable dispatchTask = () -> notificationTaskExecutor.execute(
                () -> emailNotificationService.sendAccountActivationEmail(email, username, activationLink));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask.run();
                }
            });
            return;
        }
        dispatchTask.run();
    }
}
