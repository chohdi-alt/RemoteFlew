package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManagerScoreSubmissionRequest(
        @NotNull @Min(0) @Max(100) Integer attendance,
        @NotNull @Min(0) @Max(100) Integer tasks,
        @NotNull @Min(0) @Max(100) Integer punctuality,
        @NotNull @Min(0) @Max(100) Integer behavior,
        @Size(max = 1000) String comments) {
}
