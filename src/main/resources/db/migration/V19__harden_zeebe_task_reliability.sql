-- Ensure legacy rows have a deterministic, unique job key before hardening constraints.
UPDATE zeebe_tasks
SET job_key = -id
WHERE job_key IS NULL;

-- Deduplicate any historical retry duplicates by keeping the earliest row per job key.
DELETE t1
FROM zeebe_tasks t1
JOIN zeebe_tasks t2
  ON t1.job_key = t2.job_key
 AND t1.id > t2.id;

-- Enforce mandatory job key persistence.
ALTER TABLE zeebe_tasks
    MODIFY COLUMN job_key BIGINT NOT NULL;

-- Enforce idempotency at DB level.
SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'zeebe_tasks'
      AND index_name = 'uk_zeebe_tasks_job_key'
);
SET @sql = IF(
    @idx_exists = 0,
    'CREATE UNIQUE INDEX uk_zeebe_tasks_job_key ON zeebe_tasks(job_key)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
