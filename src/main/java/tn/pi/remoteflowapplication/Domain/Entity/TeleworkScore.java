package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import tn.pi.remoteflowapplication.domain.state.ScoreStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "telework_scores",
        uniqueConstraints = @UniqueConstraint(name = "uk_telework_scores_request", columnNames = "telework_request_id"),
        indexes = {
                @Index(name = "idx_telework_scores_status", columnList = "status"),
                @Index(name = "idx_telework_scores_manager_external_id", columnList = "manager_external_id"),
                @Index(name = "idx_telework_scores_hr_external_id", columnList = "hr_external_id")
        })
public class TeleworkScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telework_request_id", nullable = false, updatable = false)
    private TeleworkRequest teleworkRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private ScoreStatus status;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "manager_external_id", length = 128)
    private String managerExternalId;

    @Column(name = "manager_comment", length = 1000)
    private String managerComment;

    @Column(name = "hr_external_id", length = 128)
    private String hrExternalId;

    @Column(name = "hr_comment", length = 1000)
    private String hrComment;

    @Column(name = "scored_at")
    private Instant scoredAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "teleworkScore", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<TeleworkScoreMetric> metrics = new ArrayList<>();

    protected TeleworkScore() {
    }

    private TeleworkScore(TeleworkRequest teleworkRequest, ScoreStatus status) {
        this.teleworkRequest = teleworkRequest;
        this.status = status;
    }

    public static TeleworkScore createPending(TeleworkRequest teleworkRequest) {
        return new TeleworkScore(teleworkRequest, ScoreStatus.PENDING_MANAGER_INPUT);
    }

    public void submitByManager(
            String managerExternalId,
            String managerComment,
            BigDecimal totalScore,
            List<TeleworkScoreMetric> breakdown,
            Instant scoredAt) {
        this.managerExternalId = sanitize(managerExternalId);
        this.managerComment = sanitize(managerComment);
        this.totalScore = totalScore;
        this.scoredAt = scoredAt == null ? Instant.now() : scoredAt;
        this.status = ScoreStatus.SCORED;

        this.metrics.clear();
        if (breakdown != null) {
            for (TeleworkScoreMetric metric : breakdown) {
                addMetric(metric);
            }
        }
    }

    public void reviewByHr(String hrExternalId, String hrComment, Instant reviewedAt) {
        this.hrExternalId = sanitize(hrExternalId);
        this.hrComment = sanitize(hrComment);
        this.reviewedAt = reviewedAt == null ? Instant.now() : reviewedAt;
        this.status = ScoreStatus.REVIEWED_BY_HR;
    }

    public void addMetric(TeleworkScoreMetric metric) {
        if (metric == null) {
            return;
        }
        metric.attachTo(this);
        this.metrics.add(metric);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public TeleworkRequest getTeleworkRequest() {
        return teleworkRequest;
    }

    public ScoreStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public String getManagerExternalId() {
        return managerExternalId;
    }

    public String getManagerComment() {
        return managerComment;
    }

    public String getHrExternalId() {
        return hrExternalId;
    }

    public String getHrComment() {
        return hrComment;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<TeleworkScoreMetric> getMetrics() {
        return Collections.unmodifiableList(metrics);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
