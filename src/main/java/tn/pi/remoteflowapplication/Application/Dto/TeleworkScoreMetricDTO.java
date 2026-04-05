package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.state.MetricCode;

import java.math.BigDecimal;

public record TeleworkScoreMetricDTO(
        MetricCode metricCode,
        BigDecimal metricValue,
        BigDecimal metricWeight,
        BigDecimal weightedScore) {
}
