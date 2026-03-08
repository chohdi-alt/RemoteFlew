package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CreateTeleworkDTO {

    @NotBlank(message = "employeeId must not be blank")
    private String employeeId;

    @NotNull(message = "startDate must not be null")
    private LocalDate startDate;

    @NotNull(message = "endDate must not be null")
    private LocalDate endDate;

    @Size(max = 500, message = "reason must not exceed 500 characters")
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
