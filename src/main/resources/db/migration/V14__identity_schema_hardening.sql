-- Align identity/audit columns with JPA expectations and Keycloak sync behavior

-- Users: allow nullable email (Keycloak may omit email), ensure actif + created_at are not null
ALTER TABLE users MODIFY COLUMN email VARCHAR(255) NULL;

UPDATE users SET actif = TRUE WHERE actif IS NULL;
ALTER TABLE users MODIFY COLUMN actif BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE users SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE users MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Teams: created_at must be non-null (AuditedEntity)
UPDATE teams SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE teams MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Telework requests: created_at must be non-null (AuditedEntity)
UPDATE telework_requests SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE telework_requests MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Roles: ensure created_at is non-null (AuditedEntity)
UPDATE roles SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE roles MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Restore analytics index aligned with entity definition
CREATE INDEX IF NOT EXISTS idx_telework_created_at ON telework_requests (created_at);
