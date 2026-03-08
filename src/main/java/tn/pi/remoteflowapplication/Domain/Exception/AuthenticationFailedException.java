package tn.pi.remoteflowapplication.domain.exception;

/**
 * Thrown when Keycloak rejects a login attempt (e.g. bad credentials,
 * account not fully set up, account disabled, etc.).
 * Maps to HTTP 401 Unauthorized.
 */
public class AuthenticationFailedException extends RuntimeException {

    private final String errorCode;
    private final String keycloakError;
    private final String keycloakDescription;
    private final Integer keycloakStatus;

    public AuthenticationFailedException(String message) {
        this(message, "AUTHENTICATION_FAILED", null, null, null, null);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        this(message, "AUTHENTICATION_FAILED", null, null, null, cause);
    }

    public AuthenticationFailedException(
            String message,
            String errorCode,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus) {
        this(message, errorCode, keycloakError, keycloakDescription, keycloakStatus, null);
    }

    public AuthenticationFailedException(
            String message,
            String errorCode,
            String keycloakError,
            String keycloakDescription,
            Integer keycloakStatus,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null || errorCode.isBlank() ? "AUTHENTICATION_FAILED" : errorCode;
        this.keycloakError = keycloakError;
        this.keycloakDescription = keycloakDescription;
        this.keycloakStatus = keycloakStatus;
    }

    public String getErrorCode() {
        return errorCode;
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
}
