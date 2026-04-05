package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.state.ScoreStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TeleworkScoreSummaryDTO(
        Long scoreId,
        Long requestId,
        String employeeId,
        Long teamId,
        LocalDate startDate,
        LocalDate endDate,
        ScoreStatus status,
        BigDecimal totalScore,
        String managerExternalId,
        Instant scoredAt,
        String hrExternalId,
        Instant reviewedAt) {
}
