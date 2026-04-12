package tn.pi.remoteflowapplication.domain.exception;

/**
 * Thrown when Keycloak rejects a login attempt (e.g. bad credentials,
 * account not fully set up, account disabled, etc.).
 * Maps to HTTP 401 Unauthorized.
 */
public class AuthenticationFailedException extends RuntimeException {

    private final String errorCode;
    private final AuthErrorCode authErrorCode;
    private final String keycloakError;
    private final String keycloakDescription;
    private final Integer keycloakStatus;
    private final Long retryAfterSeconds;

    public AuthenticationFailedException(String message) {
        this(AuthErrorCode.AUTH_INVALID, AuthErrorCode.AUTH_INVALID.name(), message, null, null, null, null, null);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        this(AuthErrorCode.AUTH_INVALID, AuthErrorCode.AUTH_INVALID.name(), message, null, null, null, null, cause);
    }

    public AuthenticationFailedException(AuthErrorCode code, String message) {
        this(code, message, null, null, null, null);
    }

    public AuthenticationFailedException(AuthErrorCode code, String message, Long retryAfterSeconds) {
        this(code, code == null ? AuthErrorCode.AUTH_INVALID.name() : code.name(), message, null, null, null, retryAfterSeconds, null);
    }

    public AuthenticationFailedException(
            AuthErrorCode code,
            String message,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus) {
        this(
                code == null ? AuthErrorCode.AUTH_INVALID : code,
                (code == null ? AuthErrorCode.AUTH_INVALID : code).name(),
                message,
                keycloakError,
                keycloakDescription,
                keycloakStatus,
                null,
                null);
    }

    public AuthenticationFailedException(
            String message,
            String errorCode,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus) {
        this(resolveAuthErrorCode(errorCode), normalizeApiErrorCode(errorCode), message, keycloakError, keycloakDescription, keycloakStatus, null, null);
    }

    public AuthenticationFailedException(
            String message,
            String errorCode,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus,
            Throwable cause) {
        this(resolveAuthErrorCode(errorCode), normalizeApiErrorCode(errorCode), message, keycloakError, keycloakDescription, keycloakStatus, null, cause);
    }

    public AuthenticationFailedException(
            AuthErrorCode code,
            String message,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus,
            Throwable cause) {
        this(code, code == null ? AuthErrorCode.AUTH_INVALID.name() : code.name(), message, keycloakError, keycloakDescription, keycloakStatus, null, cause);
    }

    private AuthenticationFailedException(
            AuthErrorCode code,
            String apiErrorCode,
            String message,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus,
            Long retryAfterSeconds,
            Throwable cause) {
        super(message, cause);
        this.authErrorCode = code == null ? AuthErrorCode.AUTH_INVALID : code;
        this.errorCode = normalizeApiErrorCode(apiErrorCode);
        this.keycloakError = keycloakError;
        this.keycloakDescription = keycloakDescription;
        this.keycloakStatus = keycloakStatus;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public AuthErrorCode getAuthErrorCode() {
        return authErrorCode;
    }

    public String getKeycloakError() {
        return keycloakError;
    }

    public String getKeycloakDescription() {
        return keycloakDescription;
    }

    public Integer getKeycloakStatus() {
        return keycloakStatus;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static AuthErrorCode resolveAuthErrorCode(String value) {
        if (value == null || value.isBlank()) {
            return AuthErrorCode.AUTH_INVALID;
        }

        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PASSWORD_UPDATE_REQUIRED" -> AuthErrorCode.PASSWORD_UPDATE_REQUIRED;
            case "AUTH_TEMP_LOCK" -> AuthErrorCode.AUTH_TEMP_LOCK;
            case "AUTH_ACCOUNT_DISABLED" -> AuthErrorCode.AUTH_ACCOUNT_DISABLED;
            case "AUTH_INVALID", "AUTHENTICATION_FAILED" -> AuthErrorCode.AUTH_INVALID;
            default -> AuthErrorCode.AUTH_INVALID;
        };
    }

    private static String normalizeApiErrorCode(String value) {
        if (value == null || value.isBlank()) {
            return AuthErrorCode.AUTH_INVALID.name();
        }
        return value.trim();
    }
}
