-- V24: Foundation tables for account activation and dynamic SMTP configuration.
-- This migration is intentionally additive and does not change existing behavior.

CREATE TABLE IF NOT EXISTS activation_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    keycloak_user_id VARCHAR(64) NOT NULL,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_activation_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_activation_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_activation_tokens_user_id ON activation_tokens (user_id);
CREATE INDEX idx_activation_tokens_keycloak_user_id ON activation_tokens (keycloak_user_id);
CREATE INDEX idx_activation_tokens_status_expires_at ON activation_tokens (status, expires_at);

CREATE TABLE IF NOT EXISTS smtp_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    protocol VARCHAR(20) NOT NULL DEFAULT 'smtp',
    username VARCHAR(255) NULL,
    password VARCHAR(500) NULL,
    from_email VARCHAR(255) NULL,
    auth_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starttls_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ssl_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    connection_timeout_ms INT NULL,
    read_timeout_ms INT NULL,
    write_timeout_ms INT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_smtp_configurations_name UNIQUE (name)
);

CREATE INDEX idx_smtp_configurations_active_updated ON smtp_configurations (active, updated_at);
