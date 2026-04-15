package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import static net.logstash.logback.argument.StructuredArguments.kv;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.config.LoginProtectionProperties;
import tn.pi.remoteflowapplication.domain.entity.LoginProtectionState;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringLoginProtectionStateJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;
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
    private final ClientIpResolver clientIpResolver;

    public LoginProtectionService(
            SpringLoginProtectionStateJpaRepository stateRepository,
            KeycloakAuthService keycloakAuthService,
            UserRepository userRepository,
            LoginProtectionProperties properties,
            ClientIpResolver clientIpResolver) {
        this.stateRepository = stateRepository;
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
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
            String ip = getClientIp();
            String eventName = "AUTH_ACCOUNT_DISABLED";
            logger.warn("AUTH_EVENT",
                    kv("event", eventName),
                    kv("event_normalized", "auth.account.disabled"),
                    kv("category", "AUTH"),
                    kv("outcome", "FAILURE"),
                    kv("user", normalizedUsername),
                    kv("keycloakError", exception.getKeycloakError()),
                    kv("keycloakStatus", exception.getKeycloakStatus()),
                    kv("traceId", getTraceId()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("severity", getSeverity(eventName)));
            return;
        }

        LoginProtectionState state = stateRepository.findByUsernameForUpdate(normalizedUsername)
                .orElseGet(() -> new LoginProtectionState(normalizedUsername));
        Instant now = Instant.now();

        String ip = getClientIp();
        String traceId = getTraceId();
        switch (failureType) {
            case TEMPORARY_LOCK -> {
                boolean newLockEvent = state.registerTemporaryLock(
                        now,
                        properties.failureWindow(),
                        properties.temporaryLockDedupWindow());
                String eventName = "AUTH_TEMP_LOCK";
                logger.warn("AUTH_EVENT",
                        kv("event", eventName),
                        kv("event_normalized", "auth.lock.temporary"),
                        kv("category", "AUTH"),
                        kv("outcome", "FAILURE"),
                        kv("user", normalizedUsername),
                        kv("newLockEvent", newLockEvent),
                        kv("failureCount", state.getFailureCount()),
                        kv("lockEventCount", state.getTemporaryLockCount()),
                        kv("windowMinutes", properties.getFailureWindowMinutes()),
                        kv("traceId", traceId),
                        kv("ip", ip),
                        kv("ip_private", isPrivateIp(ip)),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"),
                        kv("severity", getSeverity(eventName)));
            }
            default -> {
                state.registerCredentialFailure(now, properties.failureWindow());
                String eventName = "AUTH_FAILURE_TRACKED";
                logger.warn("AUTH_EVENT",
                        kv("event", eventName),
                        kv("event_normalized", "auth.failure.tracked"),
                        kv("category", "AUTH"),
                        kv("outcome", "FAILURE"),
                        kv("user", normalizedUsername),
                        kv("failureCount", state.getFailureCount()),
                        kv("lockEventCount", state.getTemporaryLockCount()),
                        kv("keycloakError", exception.getKeycloakError()),
                        kv("keycloakStatus", exception.getKeycloakStatus()),
                        kv("traceId", traceId),
                        kv("ip", ip),
                        kv("ip_private", isPrivateIp(ip)),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"),
                        kv("severity", getSeverity(eventName)));
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
        String ip = getClientIp();
        String eventName = "AUTH_LOGIN_PROTECTION_RESET";
        logger.info("AUTH_EVENT",
                kv("event", eventName),
                kv("event_normalized", "auth.protection.reset"),
                kv("category", "AUTH"),
                kv("outcome", "SUCCESS"),
                kv("user", normalizedUsername),
                kv("reason", "admin-reactivation"),
                kv("traceId", getTraceId()),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("severity", getSeverity(eventName)));
    }

    private void escalateToHardLock(String normalizedUsername, LoginProtectionState state) {
        Optional<String> keycloakUserId = keycloakAuthService.findUserIdByUsername(normalizedUsername);
        if (keycloakUserId.isEmpty()) {
            String ip = getClientIp();
            String eventName = "AUTH_HARD_LOCK_SKIPPED";
            logger.warn("AUTH_EVENT",
                    kv("event", eventName),
                    kv("event_normalized", "auth.lock.hard.skipped"),
                    kv("category", "AUTH"),
                    kv("outcome", "FAILURE"),
                    kv("user", normalizedUsername),
                    kv("reason", "user-not-found"),
                    kv("failureCount", state.getFailureCount()),
                    kv("lockEventCount", state.getTemporaryLockCount()),
                    kv("traceId", getTraceId()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("severity", getSeverity(eventName)));
            return;
        }

        String userId = keycloakUserId.get();
        keycloakAuthService.setEnabled(userId, false);
        userRepository.findByKeycloakId(userId).ifPresent(user -> {
            user.updateActivation(false);
            userRepository.save(user);
        });
        state.markHardLocked(Instant.now());
        String ip = getClientIp();
        String eventName = "AUTH_HARD_LOCK_APPLIED";
        logger.error("AUTH_EVENT",
                kv("event", eventName),
                kv("event_normalized", "auth.lock.hard"),
                kv("category", "AUTH"),
                kv("outcome", "FAILURE"),
                kv("user", normalizedUsername),
                kv("keycloakUserId", userId),
                kv("failureCount", state.getFailureCount()),
                kv("lockEventCount", state.getTemporaryLockCount()),
                kv("thresholdFailures", properties.getHardLockThresholdFailures()),
                kv("thresholdLockEvents", properties.getHardLockThresholdLockEvents()),
                kv("traceId", getTraceId()),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("severity", getSeverity(eventName)));
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

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null)
            return "unknown";

        HttpServletRequest request = attrs.getRequest();
        return clientIpResolver.resolve(request);
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null) ? traceId : "N/A";
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || "unknown".equals(ip))
            return false;
        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && is172Private(ip));
    }

    private boolean is172Private(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length < 2)
                return false;
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    private String getSeverity(String event) {
        if (event == null)
            return "UNKNOWN";
        return switch (event) {
            case "AUTH_INVALID", "AUTH_FAILURE_TRACKED", "AUTH_LOGIN_FAILURE" -> "LOW";
            case "AUTH_TEMP_LOCK" -> "MEDIUM";
            case "AUTH_ACCOUNT_DISABLED", "AUTH_HARD_LOCK_APPLIED" -> "HIGH";
            case "PASSWORD_UPDATE_REQUIRED" -> "MEDIUM";
            case "AUTH_SUCCESS", "AUTH_LOGIN_PROTECTION_RESET" -> "LOW";
            default -> "UNKNOWN";
        };
    }

    private enum FailureType {
        INVALID_CREDENTIALS,
        TEMPORARY_LOCK,
        ACCOUNT_DISABLED,
        PASSWORD_UPDATE_REQUIRED,
        OTHER
    }
}
