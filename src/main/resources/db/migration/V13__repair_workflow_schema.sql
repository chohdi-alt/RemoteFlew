-- Repair workflow tables to match domain entities
SET @dbname = DATABASE();

-- workflow_instances repair
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS process_key VARCHAR(255) NULL;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS statut VARCHAR(255) NULL;

SET @wf_instances_has_process_instance_id = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'workflow_instances'
      AND COLUMN_NAME = 'process_instance_id'
);

SET @wf_instances_copy_process_key_query = IF(
    @wf_instances_has_process_instance_id > 0,
    'UPDATE workflow_instances SET process_key = process_instance_id WHERE process_key IS NULL AND process_instance_id IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_copy_process_key FROM @wf_instances_copy_process_key_query;
EXECUTE stmt_copy_process_key;
DEALLOCATE PREPARE stmt_copy_process_key;

SET @wf_instances_has_current_step = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'workflow_instances'
      AND COLUMN_NAME = 'current_step'
);

SET @wf_instances_copy_statut_query = IF(
    @wf_instances_has_current_step > 0,
    'UPDATE workflow_instances SET statut = current_step WHERE statut IS NULL AND current_step IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_copy_statut FROM @wf_instances_copy_statut_query;
EXECUTE stmt_copy_statut;
DEALLOCATE PREPARE stmt_copy_statut;

UPDATE workflow_instances
SET process_key = CONCAT('legacy-process-', id)
WHERE process_key IS NULL;

UPDATE workflow_instances
SET statut = 'UNKNOWN'
WHERE statut IS NULL;

ALTER TABLE workflow_instances MODIFY COLUMN process_key VARCHAR(255) NOT NULL;
ALTER TABLE workflow_instances MODIFY COLUMN statut VARCHAR(255) NOT NULL;

-- workflow_tasks repair
ALTER TABLE workflow_tasks ADD COLUMN IF NOT EXISTS nom VARCHAR(255) NULL;
ALTER TABLE workflow_tasks ADD COLUMN IF NOT EXISTS date_echeance TIMESTAMP NULL;
ALTER TABLE workflow_tasks ADD COLUMN IF NOT EXISTS workflow_instance_id BIGINT NULL;

SET @wf_tasks_has_task_name = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'workflow_tasks'
      AND COLUMN_NAME = 'task_name'
);

SET @wf_tasks_copy_nom_query = IF(
    @wf_tasks_has_task_name > 0,
    'UPDATE workflow_tasks SET nom = task_name WHERE nom IS NULL AND task_name IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_copy_task_nom FROM @wf_tasks_copy_nom_query;
EXECUTE stmt_copy_task_nom;
DEALLOCATE PREPARE stmt_copy_task_nom;

UPDATE workflow_tasks
SET nom = CONCAT('task-', id)
WHERE nom IS NULL;

SET @wf_tasks_has_demande_id = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'workflow_tasks'
      AND COLUMN_NAME = 'demande_id'
);

SET @wf_instances_has_demande_id = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'workflow_instances'
      AND COLUMN_NAME = 'demande_id'
);

SET @wf_insert_missing_instances_query = IF(
    @wf_tasks_has_demande_id > 0 AND @wf_instances_has_demande_id > 0,
    'INSERT INTO workflow_instances (process_key, statut, demande_id)
     SELECT CONCAT(''legacy-process-'', t.demande_id), ''UNKNOWN'', t.demande_id
     FROM (
         SELECT DISTINCT demande_id
         FROM workflow_tasks
         WHERE demande_id IS NOT NULL
     ) t
     LEFT JOIN workflow_instances wi ON wi.demande_id = t.demande_id
     WHERE wi.id IS NULL',
    'SELECT 1'
);
PREPARE stmt_insert_missing_instances FROM @wf_insert_missing_instances_query;
EXECUTE stmt_insert_missing_instances;
DEALLOCATE PREPARE stmt_insert_missing_instances;

SET @wf_tasks_link_instance_query = IF(
    @wf_tasks_has_demande_id > 0 AND @wf_instances_has_demande_id > 0,
    'UPDATE workflow_tasks wt
     JOIN workflow_instances wi ON wi.demande_id = wt.demande_id
     SET wt.workflow_instance_id = wi.id
     WHERE wt.workflow_instance_id IS NULL',
    'SELECT 1'
);
PREPARE stmt_link_tasks_instances FROM @wf_tasks_link_instance_query;
EXECUTE stmt_link_tasks_instances;
DEALLOCATE PREPARE stmt_link_tasks_instances;

UPDATE workflow_tasks
SET workflow_instance_id = (
    SELECT MIN(id) FROM workflow_instances
)
WHERE workflow_instance_id IS NULL
  AND (SELECT COUNT(*) FROM workflow_instances) > 0;

ALTER TABLE workflow_tasks MODIFY COLUMN nom VARCHAR(255) NOT NULL;
ALTER TABLE workflow_tasks MODIFY COLUMN workflow_instance_id BIGINT NOT NULL;

SET @wf_tasks_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'workflow_tasks'
      AND COLUMN_NAME = 'workflow_instance_id'
      AND REFERENCED_TABLE_NAME = 'workflow_instances'
      AND REFERENCED_COLUMN_NAME = 'id'
);

SET @wf_tasks_fk_orphans = (
    SELECT COUNT(*)
    FROM workflow_tasks wt
    LEFT JOIN workflow_instances wi ON wi.id = wt.workflow_instance_id
    WHERE wt.workflow_instance_id IS NOT NULL
      AND wi.id IS NULL
);

SET @add_wf_tasks_fk_query = IF(
    @wf_tasks_fk_exists = 0 AND @wf_tasks_fk_orphans = 0,
    'ALTER TABLE workflow_tasks ADD CONSTRAINT fk_workflow_tasks_instance FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instances(id)',
    'SELECT 1'
);
PREPARE stmt_add_wf_tasks_fk FROM @add_wf_tasks_fk_query;
EXECUTE stmt_add_wf_tasks_fk;
DEALLOCATE PREPARE stmt_add_wf_tasks_fk;
