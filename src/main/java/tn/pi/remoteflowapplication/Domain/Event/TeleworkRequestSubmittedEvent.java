package tn.pi.remoteflowapplication.domain.event;

import java.time.LocalDate;

public class TeleworkRequestSubmittedEvent extends DomainEvent {

    private final Long requestId;
    private final String employeeId;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public TeleworkRequestSubmittedEvent(
            Long requestId,
            String employeeId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.requestId = requestId;
        this.employeeId = employeeId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
