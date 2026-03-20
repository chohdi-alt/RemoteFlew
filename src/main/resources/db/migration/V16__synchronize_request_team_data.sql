-- V16__synchronize_request_team_data.sql
-- Synchronize redundant team and creator columns in telework_requests

-- 1. Sync utilisateur_id (createur) from employee_id (external_id)
UPDATE telework_requests tr
JOIN users u ON tr.employee_id = u.external_id
SET tr.utilisateur_id = u.id
WHERE tr.utilisateur_id IS NULL;

-- 2. Sync team_id (Long field) from equipe_id (Relationship FK) if exists
UPDATE telework_requests tr
SET tr.team_id = tr.equipe_id
WHERE tr.team_id IS NULL AND tr.equipe_id IS NOT NULL;

-- 3. Sync equipe_id (Relationship FK) from team_id (Long field) if exists
UPDATE telework_requests tr
SET tr.equipe_id = tr.team_id
WHERE tr.equipe_id IS NULL AND tr.team_id IS NOT NULL;

-- 4. If both are null, sync from the employee's current team assignment
UPDATE telework_requests tr
JOIN users u ON tr.employee_id = u.external_id
SET tr.team_id = u.team_id, tr.equipe_id = u.team_id
WHERE tr.team_id IS NULL AND u.team_id IS NOT NULL;
