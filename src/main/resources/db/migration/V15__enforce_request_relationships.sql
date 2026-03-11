-- Enforce mandatory relationships to align with JPA mappings

-- Justificatifs must belong to a telework request
DELETE FROM justificatifs WHERE demande_id IS NULL;
ALTER TABLE justificatifs MODIFY COLUMN demande_id BIGINT NOT NULL;

-- Notifications must belong to a telework request
DELETE FROM notifications WHERE demande_id IS NULL;
ALTER TABLE notifications MODIFY COLUMN demande_id BIGINT NOT NULL;

-- Workflow instances must belong to a telework request
DELETE FROM workflow_instances WHERE demande_id IS NULL;
ALTER TABLE workflow_instances MODIFY COLUMN demande_id BIGINT NOT NULL;

-- Add missing FK for audit_logs.workflow_task_id when safe
SET @dbname = DATABASE();

SET @audit_wf_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'audit_logs'
      AND COLUMN_NAME = 'workflow_task_id'
      AND REFERENCED_TABLE_NAME = 'workflow_tasks'
      AND REFERENCED_COLUMN_NAME = 'id'
);

SET @audit_wf_orphans = (
    SELECT COUNT(*)
    FROM audit_logs al
    LEFT JOIN workflow_tasks wt ON wt.id = al.workflow_task_id
    WHERE al.workflow_task_id IS NOT NULL
      AND wt.id IS NULL
);

SET @add_audit_wf_fk_query = IF(
    @audit_wf_fk_exists = 0 AND @audit_wf_orphans = 0,
    'ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_workflow_task FOREIGN KEY (workflow_task_id) REFERENCES workflow_tasks(id)',
    'SELECT 1'
);
PREPARE stmt_add_audit_wf_fk FROM @add_audit_wf_fk_query;
EXECUTE stmt_add_audit_wf_fk;
DEALLOCATE PREPARE stmt_add_audit_wf_fk;
