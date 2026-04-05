-- V25: Telework scoring tables (additive, traceable, one score per request).

CREATE TABLE IF NOT EXISTS telework_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    telework_request_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_score DECIMAL(5,2) NULL,
    manager_external_id VARCHAR(128) NULL,
    manager_comment VARCHAR(1000) NULL,
    hr_external_id VARCHAR(128) NULL,
    hr_comment VARCHAR(1000) NULL,
    scored_at DATETIME(6) NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_telework_scores_request UNIQUE (telework_request_id),
    CONSTRAINT fk_telework_scores_request FOREIGN KEY (telework_request_id) REFERENCES telework_requests (id)
);

CREATE INDEX idx_telework_scores_status ON telework_scores (status);
CREATE INDEX idx_telework_scores_manager_external_id ON telework_scores (manager_external_id);
CREATE INDEX idx_telework_scores_hr_external_id ON telework_scores (hr_external_id);

CREATE TABLE IF NOT EXISTS telework_score_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    telework_score_id BIGINT NOT NULL,
    metric_code VARCHAR(40) NOT NULL,
    metric_value DECIMAL(5,2) NOT NULL,
    metric_weight DECIMAL(5,2) NOT NULL,
    weighted_score DECIMAL(6,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_telework_score_metrics_score FOREIGN KEY (telework_score_id) REFERENCES telework_scores (id) ON DELETE CASCADE,
    CONSTRAINT uk_telework_score_metrics_unique UNIQUE (telework_score_id, metric_code)
);

CREATE INDEX idx_telework_score_metrics_score_id ON telework_score_metrics (telework_score_id);
CREATE INDEX idx_telework_score_metrics_code ON telework_score_metrics (metric_code);
