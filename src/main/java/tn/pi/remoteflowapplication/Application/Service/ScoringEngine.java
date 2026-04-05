package tn.pi.remoteflowapplication.application.service;

import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.domain.state.MetricCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ScoringEngine {

    private static final BigDecimal SCORE_SCALE = BigDecimal.valueOf(100);

    private static final Map<MetricCode, BigDecimal> WEIGHTS = Map.copyOf(new EnumMap<>(Map.of(
            MetricCode.ATTENDANCE, BigDecimal.valueOf(0.30),
            MetricCode.TASKS, BigDecimal.valueOf(0.30),
            MetricCode.PUNCTUALITY, BigDecimal.valueOf(0.20),
            MetricCode.BEHAVIOR, BigDecimal.valueOf(0.20))));

    public ScoreResult calculate(
            BigDecimal attendance,
            BigDecimal tasks,
            BigDecimal punctuality,
            BigDecimal behavior) {

        EnumMap<MetricCode, BigDecimal> values = new EnumMap<>(MetricCode.class);
        values.put(MetricCode.ATTENDANCE, sanitizeMetric(attendance));
        values.put(MetricCode.TASKS, sanitizeMetric(tasks));
        values.put(MetricCode.PUNCTUALITY, sanitizeMetric(punctuality));
        values.put(MetricCode.BEHAVIOR, sanitizeMetric(behavior));

        List<MetricBreakdown> breakdown = values.entrySet().stream()
                .map(entry -> toBreakdown(entry.getKey(), entry.getValue()))
                .toList();

        BigDecimal totalScore = breakdown.stream()
                .map(MetricBreakdown::weightedScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new ScoreResult(totalScore, breakdown);
    }

    private MetricBreakdown toBreakdown(MetricCode code, BigDecimal value) {
        BigDecimal weight = WEIGHTS.get(code);
        BigDecimal weightedScore = value
                .multiply(weight)
                .setScale(2, RoundingMode.HALF_UP);
        return new MetricBreakdown(code, value, weight, weightedScore);
    }

    private BigDecimal sanitizeMetric(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Metric value is required.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(SCORE_SCALE) > 0) {
            throw new IllegalArgumentException("Metric value must be between 0 and 100.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record MetricBreakdown(
            MetricCode metricCode,
            BigDecimal metricValue,
            BigDecimal metricWeight,
            BigDecimal weightedScore) {
    }

    public record ScoreResult(
            BigDecimal totalScore,
            List<MetricBreakdown> metrics) {
    }
}
