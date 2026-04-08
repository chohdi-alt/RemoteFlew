package tn.pi.remoteflowapplication.domain.event;

public class TeleworkRequestManagerApprovedEvent extends DomainEvent {

    private final Long requestId;
    private final String employeeId;

    public TeleworkRequestManagerApprovedEvent(Long requestId, String employeeId) {
        super();
        this.requestId = requestId;
        this.employeeId = employeeId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getEmployeeId() {
        return employeeId;
    }
}
