package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TeleworkQuotaConfigRequest(
        @NotNull(message = "maxDaysPerWeek must not be null")
        @Min(value = 1, message = "maxDaysPerWeek must be greater than or equal to 1")
        Integer maxDaysPerWeek) {
}
