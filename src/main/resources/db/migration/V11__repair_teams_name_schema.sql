-- Repair teams table to match domain entity (Equipe/Team)
SET @dbname = DATABASE();

-- Rename legacy nom -> name only when required
SELECT COUNT(*) INTO @teams_has_nom
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname
  AND TABLE_NAME = 'teams'
  AND COLUMN_NAME = 'nom';

SELECT COUNT(*) INTO @teams_has_name
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname
  AND TABLE_NAME = 'teams'
  AND COLUMN_NAME = 'name';

SET @rename_teams_query = IF(
    @teams_has_nom > 0 AND @teams_has_name = 0,
    'ALTER TABLE teams RENAME COLUMN nom TO name',
    'SELECT 1'
);
PREPARE stmt_rename_teams FROM @rename_teams_query;
EXECUTE stmt_rename_teams;
DEALLOCATE PREPARE stmt_rename_teams;

-- Ensure the column exists even on drifted schemas
ALTER TABLE teams ADD COLUMN IF NOT EXISTS name VARCHAR(255);

-- If both nom and name exist, backfill name from nom
SELECT COUNT(*) INTO @teams_has_nom_after
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname
  AND TABLE_NAME = 'teams'
  AND COLUMN_NAME = 'nom';

SET @backfill_name_query = IF(
    @teams_has_nom_after > 0,
    'UPDATE teams SET name = nom WHERE name IS NULL AND nom IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_backfill_name FROM @backfill_name_query;
EXECUTE stmt_backfill_name;
DEALLOCATE PREPARE stmt_backfill_name;

ALTER TABLE teams MODIFY COLUMN name VARCHAR(255) NOT NULL;

-- Add unique constraint on team name when absent and data is clean
SET @teams_name_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'teams'
      AND NON_UNIQUE = 0
      AND COLUMN_NAME = 'name'
);

SET @teams_name_duplicates = (
    SELECT COUNT(*)
    FROM (
        SELECT name
        FROM teams
        WHERE name IS NOT NULL
        GROUP BY name
        HAVING COUNT(*) > 1
    ) dup_name
);

SET @add_unique_name_query = IF(
    @teams_name_unique_exists = 0 AND @teams_name_duplicates = 0,
    'ALTER TABLE teams ADD CONSTRAINT uk_team_name UNIQUE (name)',
    'SELECT 1'
);
PREPARE stmt_add_unique_name FROM @add_unique_name_query;
EXECUTE stmt_add_unique_name;
DEALLOCATE PREPARE stmt_add_unique_name;

-- Add unique constraint on team code when absent and data is clean
SET @teams_has_code = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'teams'
      AND COLUMN_NAME = 'code'
);

SET @teams_code_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'teams'
      AND NON_UNIQUE = 0
      AND COLUMN_NAME = 'code'
);

SET @teams_code_duplicates = (
    SELECT COUNT(*)
    FROM (
        SELECT code
        FROM teams
        WHERE code IS NOT NULL
        GROUP BY code
        HAVING COUNT(*) > 1
    ) dup_code
);

SET @add_unique_code_query = IF(
    @teams_has_code > 0 AND @teams_code_unique_exists = 0 AND @teams_code_duplicates = 0,
    'ALTER TABLE teams ADD CONSTRAINT uk_team_code UNIQUE (code)',
    'SELECT 1'
);
PREPARE stmt_add_unique_code FROM @add_unique_code_query;
EXECUTE stmt_add_unique_code;
DEALLOCATE PREPARE stmt_add_unique_code;
