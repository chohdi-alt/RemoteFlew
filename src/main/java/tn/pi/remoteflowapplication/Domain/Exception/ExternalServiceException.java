package tn.pi.remoteflowapplication.domain.exception;

public class ExternalServiceException extends RuntimeException {

    private final Integer upstreamStatus;
    private final String upstreamBody;
    private final String upstreamUrl;
    private final String operation;

    public ExternalServiceException(String message) {
        this(message, null, null, null, null, null);
    }

    public ExternalServiceException(String message, Throwable cause) {
        this(message, cause, null, null, null, null);
    }

    public ExternalServiceException(
            String message,
            Integer upstreamStatus,
            String upstreamBody,
            String upstreamUrl,
            String operation) {
        this(message, null, upstreamStatus, upstreamBody, upstreamUrl, operation);
    }

    public ExternalServiceException(
            String message,
            Throwable cause,
            Integer upstreamStatus,
            String upstreamBody,
            String upstreamUrl,
            String operation) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.upstreamBody = upstreamBody;
        this.upstreamUrl = upstreamUrl;
        this.operation = operation;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getUpstreamBody() {
        return upstreamBody;
    }

    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    public String getOperation() {
        return operation;
    }
}
