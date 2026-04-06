package tn.pi.remoteflowapplication.domain.exception;

public class KeycloakConflictException extends ExternalServiceException {

    public KeycloakConflictException(String message) {
        super(message);
    }

    public KeycloakConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
