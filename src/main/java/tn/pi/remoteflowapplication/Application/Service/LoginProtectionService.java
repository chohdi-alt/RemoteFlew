package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.config.LoginProtectionProperties;
import tn.pi.remoteflowapplication.domain.entity.LoginProtectionState;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringLoginProtectionStateJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class LoginProtectionService {

    private static final Logger logger = LoggerFactory.getLogger(LoginProtectionService.class);

    private final SpringLoginProtectionStateJpaRepository stateRepository;
    private final KeycloakAuthService keycloakAuthService;
    private final UserRepository userRepository;
    private final LoginProtectionProperties properties;

    public LoginProtectionService(
            SpringLoginProtectionStateJpaRepository stateRepository,
            KeycloakAuthService keycloakAuthService,
            UserRepository userRepository,
            LoginProtectionProperties properties) {
        this.stateRepository = stateRepository;
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional
    public void onLoginFailure(String username, AuthenticationFailedException exception) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null || exception == null) {
            return;
        }

        FailureType failureType = classify(exception);
        if (failureType == FailureType.PASSWORD_UPDATE_REQUIRED) {
            return;
        }
        if (failureType == FailureType.ACCOUNT_DISABLED) {
            logger.warn(
                    "event=AUTH_DISABLED_ACCOUNT_ATTEMPT username={} keycloakError={} keycloakStatus={}",
                    normalizedUsername,
                    exception.getKeycloakError(),
                    exception.getKeycloakStatus());
            return;
        }

        LoginProtectionState state = stateRepository.findByUsernameForUpdate(normalizedUsername)
                .orElseGet(() -> new LoginProtectionState(normalizedUsername));
        Instant now = Instant.now();

        switch (failureType) {
            case TEMPORARY_LOCK -> {
                boolean newLockEvent = state.registerTemporaryLock(
                        now,
                        properties.failureWindow(),
                        properties.temporaryLockDedupWindow());
                logger.warn(
                        "event=AUTH_TEMP_LOCK username={} newLockEvent={} failureCount={} lockEventCount={} windowMinutes={}",
                        normalizedUsername,
                        newLockEvent,
                        state.getFailureCount(),
                        state.getTemporaryLockCount(),
                        properties.getFailureWindowMinutes());
            }
            default -> {
                state.registerCredentialFailure(now, properties.failureWindow());
                logger.warn(
                        "event=AUTH_FAILURE_TRACKED username={} failureCount={} lockEventCount={} keycloakError={} keycloakStatus={}",
                        normalizedUsername,
                        state.getFailureCount(),
                        state.getTemporaryLockCount(),
                        exception.getKeycloakError(),
                        exception.getKeycloakStatus());
            }
        }

        if (properties.isHardLockEnabled()
                && !state.isHardLocked()
                && state.shouldEscalateToHardLock(
                        properties.getHardLockThresholdFailures(),
                        properties.getHardLockThresholdLockEvents())) {
            escalateToHardLock(normalizedUsername, state);
        }

        stateRepository.save(state);
    }

    @Transactional
    public void onLoginSuccess(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return;
        }
        stateRepository.deleteByUsername(normalizedUsername);
    }

    @Transactional
    public void clearTracking(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return;
        }
        stateRepository.deleteByUsername(normalizedUsername);
        logger.info("event=AUTH_LOGIN_PROTECTION_RESET username={} reason=admin-reactivation", normalizedUsername);
    }

    private void escalateToHardLock(String normalizedUsername, LoginProtectionState state) {
        Optional<String> keycloakUserId = keycloakAuthService.findUserIdByUsername(normalizedUsername);
        if (keycloakUserId.isEmpty()) {
            logger.warn(
                    "event=AUTH_HARD_LOCK_SKIPPED username={} reason=user-not-found failureCount={} lockEventCount={}",
                    normalizedUsername,
                    state.getFailureCount(),
                    state.getTemporaryLockCount());
            return;
        }

        String userId = keycloakUserId.get();
        keycloakAuthService.setEnabled(userId, false);
        userRepository.findByKeycloakId(userId).ifPresent(user -> {
            user.updateActivation(false);
            userRepository.save(user);
        });
        state.markHardLocked(Instant.now());
        logger.error(
                "event=AUTH_HARD_LOCK_APPLIED username={} keycloakUserId={} failureCount={} lockEventCount={} thresholdFailures={} thresholdLockEvents={}",
                normalizedUsername,
                userId,
                state.getFailureCount(),
                state.getTemporaryLockCount(),
                properties.getHardLockThresholdFailures(),
                properties.getHardLockThresholdLockEvents());
    }

    private FailureType classify(AuthenticationFailedException exception) {
        String errorCode = normalize(exception.getErrorCode());
        if ("password_update_required".equals(errorCode)) {
            return FailureType.PASSWORD_UPDATE_REQUIRED;
        }

        String keycloakDescription = normalize(exception.getKeycloakDescription());
        String message = normalize(exception.getMessage());
        String merged = (keycloakDescription + " " + message).trim();

        if (containsAny(merged, "temporarily disabled", "brute force", "locked")) {
            return FailureType.TEMPORARY_LOCK;
        }
        if (containsAny(merged, "account disabled", "user is disabled")) {
            return FailureType.ACCOUNT_DISABLED;
        }

        String keycloakError = normalize(exception.getKeycloakError());
        Integer status = exception.getKeycloakStatus();
        if ("invalid_grant".equals(keycloakError)
                || containsAny(merged, "invalid user credentials", "invalid username or password")
                || status != null && (status == 400 || status == 401)) {
            return FailureType.INVALID_CREDENTIALS;
        }
        return FailureType.OTHER;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private enum FailureType {
        INVALID_CREDENTIALS,
        TEMPORARY_LOCK,
        ACCOUNT_DISABLED,
        PASSWORD_UPDATE_REQUIRED,
        OTHER
    }
}
