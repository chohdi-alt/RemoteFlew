package tn.pi.remoteflowapplication.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateTeleworkDTO {

    @NotNull(message = "startDate must not be null")
    private LocalDate startDate;

    @NotNull(message = "endDate must not be null")
    private LocalDate endDate;

    @Size(max = 500, message = "reason must not exceed 500 characters")
    private String reason;

    public CreateTeleworkDTO() {
    }

    public CreateTeleworkDTO(
            LocalDate startDate,
            LocalDate endDate,
            String reason
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
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
