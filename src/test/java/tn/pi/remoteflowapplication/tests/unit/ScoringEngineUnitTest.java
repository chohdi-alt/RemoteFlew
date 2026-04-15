package tn.pi.remoteflowapplication.tests.unit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tn.pi.remoteflowapplication.application.service.ScoringEngine;
import tn.pi.remoteflowapplication.domain.state.MetricCode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class ScoringEngineUnitTest {

    private final ScoringEngine scoringEngine = new ScoringEngine();

    @Test
    void shouldCalculateWeightedScoreWithExpectedBreakdown() {
        ScoringEngine.ScoreResult result = scoringEngine.calculate(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(70));

        assertEquals(new BigDecimal("86.00"), result.totalScore());

        Map<MetricCode, BigDecimal> weighted = result.metrics().stream()
                .collect(Collectors.toMap(
                        ScoringEngine.MetricBreakdown::metricCode,
                        ScoringEngine.MetricBreakdown::weightedScore));

        assertEquals(new BigDecimal("30.00"), weighted.get(MetricCode.ATTENDANCE));
        assertEquals(new BigDecimal("24.00"), weighted.get(MetricCode.TASKS));
        assertEquals(new BigDecimal("18.00"), weighted.get(MetricCode.PUNCTUALITY));
        assertEquals(new BigDecimal("14.00"), weighted.get(MetricCode.BEHAVIOR));
    }

    @Test
    void shouldRoundToTwoDecimalsForNonIntegerTotals() {
        ScoringEngine.ScoreResult result = scoringEngine.calculate(
                BigDecimal.valueOf(83),
                BigDecimal.valueOf(77),
                BigDecimal.valueOf(91),
                BigDecimal.valueOf(68));

        assertEquals(new BigDecimal("79.80"), result.totalScore());
    }

    @Test
    void shouldRejectOutOfRangeMetricValues() {
        assertThrows(IllegalArgumentException.class, () -> scoringEngine.calculate(
                BigDecimal.valueOf(101),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(70)));
        
        assertThrows(IllegalArgumentException.class, () -> scoringEngine.calculate(
                BigDecimal.valueOf(-1),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(70)));
    }

    @Test
    void shouldHandleMinAndMaxBoundaries() {
        // Max all
        ScoringEngine.ScoreResult maxResult = scoringEngine.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100));
        assertEquals(new BigDecimal("100.00"), maxResult.totalScore());

        // Min all
        ScoringEngine.ScoreResult minResult = scoringEngine.calculate(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), minResult.totalScore());
    }
}
