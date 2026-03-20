-- Introduce dual Keycloak identity model:
-- - keycloak_id (immutable identifier from JWT sub)
-- - username (business identifier from JWT preferred_username)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(64);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(255);

-- Backfill keycloak_id from legacy external_id.
UPDATE users
SET keycloak_id = NULLIF(TRIM(external_id), '')
WHERE keycloak_id IS NULL OR TRIM(keycloak_id) = '';

-- Backfill username from existing business identifiers.
UPDATE users
SET username = CASE
    WHEN matricule IS NOT NULL AND TRIM(matricule) <> '' THEN TRIM(matricule)
    WHEN email IS NOT NULL AND TRIM(email) <> '' THEN SUBSTRING_INDEX(TRIM(email), '@', 1)
    ELSE NULLIF(TRIM(external_id), '')
END
WHERE username IS NULL OR TRIM(username) = '';

-- Last-resort fallback to keep migration deterministic even for corrupted legacy rows.
UPDATE users
SET keycloak_id = CONCAT('legacy-', id)
WHERE keycloak_id IS NULL OR TRIM(keycloak_id) = '';

UPDATE users
SET username = CONCAT('user-', id)
WHERE username IS NULL OR TRIM(username) = '';

-- Keep legacy columns populated for backward compatibility during transition.
UPDATE users
SET external_id = keycloak_id
WHERE external_id IS NULL OR TRIM(external_id) = '';

UPDATE users
SET matricule = username
WHERE matricule IS NULL OR TRIM(matricule) = '';

ALTER TABLE users
    MODIFY COLUMN keycloak_id VARCHAR(64) NOT NULL;

ALTER TABLE users
    MODIFY COLUMN username VARCHAR(255) NOT NULL;

-- Enforce uniqueness.
SET @idx_keycloak_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'uk_users_keycloak_id'
);
SET @sql_keycloak = IF(
    @idx_keycloak_exists = 0,
    'CREATE UNIQUE INDEX uk_users_keycloak_id ON users(keycloak_id)',
    'SELECT 1'
);
PREPARE stmt_keycloak FROM @sql_keycloak;
EXECUTE stmt_keycloak;
DEALLOCATE PREPARE stmt_keycloak;

SET @idx_username_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'uk_users_username'
);
SET @sql_username = IF(
    @idx_username_exists = 0,
    'CREATE UNIQUE INDEX uk_users_username ON users(username)',
    'SELECT 1'
);
PREPARE stmt_username FROM @sql_username;
EXECUTE stmt_username;
DEALLOCATE PREPARE stmt_username;
