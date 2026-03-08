CREATE TABLE IF NOT EXISTS system_configuration (
    `key` VARCHAR(128) NOT NULL PRIMARY KEY,
    `value` VARCHAR(512) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_configuration (`key`, `value`, updated_at)
VALUES ('telework.max-days-per-week', '1', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    `value` = VALUES(`value`),
    updated_at = CURRENT_TIMESTAMP;
