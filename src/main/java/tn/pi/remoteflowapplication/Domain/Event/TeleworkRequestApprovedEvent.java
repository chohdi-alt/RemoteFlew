package tn.pi.remoteflowapplication.domain.event;

public class TeleworkRequestApprovedEvent extends DomainEvent {

    private final Long requestId;
    private final String employeeId;
    private final String decisionComment;

    public TeleworkRequestApprovedEvent(
            Long requestId,
            String employeeId,
            String decisionComment
    ) {
        this.requestId = requestId;
        this.employeeId = employeeId;
        this.decisionComment = decisionComment;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getDecisionComment() {
        return decisionComment;
    }
}
