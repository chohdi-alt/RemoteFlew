-- STEP 7 — Create/Repair system configuration table

-- 1. Create the new table if it doesn't exist
CREATE TABLE IF NOT EXISTS system_configuration_new (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Try to migrate data from the old table schema (key/value) if it exists
-- We use a selective INSERT that only works if columns exist.
-- Since MySQL/MariaDB don't support conditional INSERT SELECT easily without procedures,
-- and procedures can be tricky with Flyway, we'll try to use a safer approach:
-- We check if we NEED to migrate.

-- If 'system_configuration' exists, we'll try to move its data to '_new' and drop it.
-- We'll do this safely.

SET @dbname = DATABASE();
SELECT COUNT(*) INTO @table_exists FROM information_schema.tables WHERE table_name = 'system_configuration' AND table_schema = @dbname;

SET @migrate_query = IF(@table_exists > 0, 
    'INSERT IGNORE INTO system_configuration_new (config_key, config_value, updated_at) SELECT `key`, `value`, updated_at FROM system_configuration', 
    'SELECT 1');
PREPARE stmt1 FROM @migrate_query;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @drop_query = IF(@table_exists > 0, 'DROP TABLE system_configuration', 'SELECT 1');
PREPARE stmt2 FROM @drop_query;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 3. Rename the new table to the final name
RENAME TABLE system_configuration_new TO system_configuration;

-- 4. Initial/Default data
INSERT INTO system_configuration(config_key, config_value)
VALUES ('telework.max-days-per-week','1')
ON DUPLICATE KEY UPDATE config_value=config_value;
