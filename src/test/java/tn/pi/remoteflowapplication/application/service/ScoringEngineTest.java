package tn.pi.remoteflowapplication.application.service;

import org.junit.jupiter.api.Test;
import tn.pi.remoteflowapplication.domain.state.MetricCode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoringEngineTest {

    private final ScoringEngine scoringEngine = new ScoringEngine();

    @Test
    void shouldCalculateWeightedScoreWithExpectedTotal() {
        ScoringEngine.ScoreResult result = scoringEngine.calculate(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(70));

        assertEquals(new BigDecimal("86.00"), result.totalScore());

        Map<MetricCode, BigDecimal> weighted = result.metrics().stream()
                .collect(Collectors.toMap(ScoringEngine.MetricBreakdown::metricCode, ScoringEngine.MetricBreakdown::weightedScore));

        assertEquals(new BigDecimal("30.00"), weighted.get(MetricCode.ATTENDANCE));
        assertEquals(new BigDecimal("24.00"), weighted.get(MetricCode.TASKS));
        assertEquals(new BigDecimal("18.00"), weighted.get(MetricCode.PUNCTUALITY));
        assertEquals(new BigDecimal("14.00"), weighted.get(MetricCode.BEHAVIOR));
    }

    @Test
    void shouldRoundToTwoDecimalPlaces() {
        ScoringEngine.ScoreResult result = scoringEngine.calculate(
                BigDecimal.valueOf(83),
                BigDecimal.valueOf(77),
                BigDecimal.valueOf(91),
                BigDecimal.valueOf(68));

        assertEquals(new BigDecimal("79.80"), result.totalScore());
    }

    @Test
    void shouldRejectOutOfRangeMetric() {
        assertThrows(IllegalArgumentException.class, () -> scoringEngine.calculate(
                BigDecimal.valueOf(120),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(70)));
    }
}
