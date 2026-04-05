package tn.pi.remoteflowapplication.domain;

import org.junit.jupiter.api.Test;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScore;
import tn.pi.remoteflowapplication.domain.entity.TeleworkScoreMetric;
import tn.pi.remoteflowapplication.domain.state.MetricCode;
import tn.pi.remoteflowapplication.domain.state.ScoreStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TeleworkScoreDomainTest {

    @Test
    void shouldCreatePendingScoreAndKeepMetricBreakdown() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee-one",
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(2));

        TeleworkScore score = TeleworkScore.createPending(request);
        assertEquals(ScoreStatus.PENDING_MANAGER_INPUT, score.getStatus());

        List<TeleworkScoreMetric> metrics = List.of(
                new TeleworkScoreMetric(MetricCode.ATTENDANCE, new BigDecimal("100.00"), new BigDecimal("0.30"), new BigDecimal("30.00")),
                new TeleworkScoreMetric(MetricCode.TASKS, new BigDecimal("80.00"), new BigDecimal("0.30"), new BigDecimal("24.00")),
                new TeleworkScoreMetric(MetricCode.PUNCTUALITY, new BigDecimal("90.00"), new BigDecimal("0.20"), new BigDecimal("18.00")),
                new TeleworkScoreMetric(MetricCode.BEHAVIOR, new BigDecimal("70.00"), new BigDecimal("0.20"), new BigDecimal("14.00")));

        score.submitByManager(
                "manager-sub-id",
                "Quality work done despite lower behavior score.",
                new BigDecimal("86.00"),
                metrics,
                Instant.now());

        assertEquals(ScoreStatus.SCORED, score.getStatus());
        assertEquals(new BigDecimal("86.00"), score.getTotalScore());
        assertEquals(4, score.getMetrics().size());
        assertNotNull(score.getScoredAt());

        score.reviewByHr("hr-sub-id", "Reviewed and accepted.", Instant.now());
        assertEquals(ScoreStatus.REVIEWED_BY_HR, score.getStatus());
        assertNotNull(score.getReviewedAt());
    }
}
