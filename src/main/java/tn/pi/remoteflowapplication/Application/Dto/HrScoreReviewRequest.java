package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.Size;

public record HrScoreReviewRequest(
        @Size(max = 1000) String comments) {
}
