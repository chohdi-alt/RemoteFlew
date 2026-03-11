ALTER TABLE telework_requests ADD COLUMN submitted_at TIMESTAMP NULL;
ALTER TABLE telework_requests ADD COLUMN manager_decision_at TIMESTAMP NULL;
ALTER TABLE telework_requests ADD COLUMN hr_decision_at TIMESTAMP NULL;
ALTER TABLE telework_requests ADD COLUMN approved_at TIMESTAMP NULL;
ALTER TABLE telework_requests ADD COLUMN rejected_at TIMESTAMP NULL;
ALTER TABLE telework_requests ADD COLUMN manager_external_id VARCHAR(255) NULL;
ALTER TABLE telework_requests ADD COLUMN hr_external_id VARCHAR(255) NULL;
ALTER TABLE telework_requests ADD COLUMN team_id BIGINT NULL;

UPDATE telework_requests
SET submitted_at = COALESCE(submitted_at, created_at, CURRENT_TIMESTAMP());

ALTER TABLE telework_requests
    MODIFY COLUMN submitted_at TIMESTAMP NOT NULL;

CREATE INDEX idx_telework_employee_id ON telework_requests (employee_id);
CREATE INDEX idx_telework_status ON telework_requests (status);
CREATE INDEX idx_telework_created_at ON telework_requests (created_at);
CREATE INDEX idx_telework_manager_external_id ON telework_requests (manager_external_id);
CREATE INDEX idx_telework_hr_external_id ON telework_requests (hr_external_id);
CREATE INDEX idx_telework_team_id ON telework_requests (team_id);
CREATE INDEX idx_telework_process_instance_id ON telework_requests (process_instance_id);
