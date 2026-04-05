package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import tn.pi.remoteflowapplication.domain.state.MetricCode;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "telework_score_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_telework_score_metrics_unique",
                columnNames = { "telework_score_id", "metric_code" }),
        indexes = {
                @Index(name = "idx_telework_score_metrics_score_id", columnList = "telework_score_id"),
                @Index(name = "idx_telework_score_metrics_code", columnList = "metric_code")
        })
public class TeleworkScoreMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telework_score_id", nullable = false)
    private TeleworkScore teleworkScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private MetricCode metricCode;

    @Column(name = "metric_value", nullable = false, precision = 5, scale = 2)
    private BigDecimal metricValue;

    @Column(name = "metric_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal metricWeight;

    @Column(name = "weighted_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightedScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeleworkScoreMetric() {
    }

    public TeleworkScoreMetric(
            MetricCode metricCode,
            BigDecimal metricValue,
            BigDecimal metricWeight,
            BigDecimal weightedScore) {
        this.metricCode = metricCode;
        this.metricValue = metricValue;
        this.metricWeight = metricWeight;
        this.weightedScore = weightedScore;
    }

    public void attachTo(TeleworkScore teleworkScore) {
        this.teleworkScore = teleworkScore;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public MetricCode getMetricCode() {
        return metricCode;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public BigDecimal getMetricWeight() {
        return metricWeight;
    }

    public BigDecimal getWeightedScore() {
        return weightedScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
