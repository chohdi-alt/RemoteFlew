package tn.pi.remoteflowapplication.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManagerScoreSubmissionRequest(
        @JsonProperty("attendanceScore") @NotNull @Min(0) @Max(100) Integer attendance,
        @JsonProperty("performanceScore") @NotNull @Min(0) @Max(100) Integer tasks,
        @JsonProperty("punctualityScore") @NotNull @Min(0) @Max(100) Integer punctuality,
        @JsonProperty("behaviorScore") @NotNull @Min(0) @Max(100) Integer behavior,
        @Size(max = 1000) String comments) {
}
