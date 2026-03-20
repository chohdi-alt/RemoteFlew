CREATE TABLE IF NOT EXISTS zeebe_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    job_key BIGINT,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    assigned_to VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_zeebe_tasks_request FOREIGN KEY (request_id) REFERENCES telework_requests(id)
);

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'zeebe_tasks' AND index_name = 'idx_zeebe_tasks_request_id');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_zeebe_tasks_request_id ON zeebe_tasks(request_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'zeebe_tasks' AND index_name = 'idx_zeebe_tasks_type_status');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_zeebe_tasks_type_status ON zeebe_tasks(type, status)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'zeebe_tasks' AND index_name = 'idx_zeebe_tasks_job_key');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_zeebe_tasks_job_key ON zeebe_tasks(job_key)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
