package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDate;

public class CreateTeleworkDTO {

    private String employeeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    public CreateTeleworkDTO() {
    }

    public CreateTeleworkDTO(
            String employeeId,
            LocalDate startDate,
            LocalDate endDate,
            String reason
    ) {
        this.employeeId = employeeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }
}
