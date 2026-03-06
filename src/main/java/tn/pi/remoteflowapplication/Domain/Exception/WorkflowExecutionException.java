package tn.pi.remoteflowapplication.domain.exception;

public class WorkflowExecutionException extends RuntimeException {
    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkflowExecutionException(String message) {
        super(message);
    }
}
