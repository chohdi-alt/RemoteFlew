package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.state.ScoreStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TeleworkScoreDTO(
        Long scoreId,
        Long requestId,
        String employeeId,
        Long teamId,
        LocalDate startDate,
        LocalDate endDate,
        ScoreStatus status,
        BigDecimal totalScore,
        String managerExternalId,
        String managerComment,
        Instant scoredAt,
        String hrExternalId,
        String hrComment,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        List<TeleworkScoreMetricDTO> metrics) {
}
