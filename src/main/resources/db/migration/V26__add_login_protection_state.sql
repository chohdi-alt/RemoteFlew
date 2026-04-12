-- V26: Progressive login protection state for hard-lock escalation.
-- Tracks credential failures and temporary lock cycles without storing secrets.

CREATE TABLE IF NOT EXISTS login_protection_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    temporary_lock_count INT NOT NULL DEFAULT 0,
    first_failure_at DATETIME(6) NULL,
    last_failure_at DATETIME(6) NULL,
    last_temporary_lock_at DATETIME(6) NULL,
    hard_locked_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_login_protection_state_username UNIQUE (username)
);

CREATE INDEX idx_login_protection_state_last_failure_at ON login_protection_state (last_failure_at);
CREATE INDEX idx_login_protection_state_hard_locked_at ON login_protection_state (hard_locked_at);
