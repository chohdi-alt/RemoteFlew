package tn.pi.remoteflowapplication.domain.exception;

/**
 * Thrown when Keycloak rejects a login attempt (e.g. bad credentials,
 * account not fully set up, account disabled, etc.).
 * Maps to HTTP 401 Unauthorized.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
