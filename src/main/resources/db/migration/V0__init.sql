-- V0__init.sql
-- Baseline schema for the Telework Flow Application

-- 1. Users table (Utilisateur + User + AuditedEntity)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    nom VARCHAR(255),
    prenom VARCHAR(255),
    matricule VARCHAR(255) UNIQUE,
    actif BOOLEAN DEFAULT TRUE,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255)
);

-- 2. Teams table (Equipe + Team + AuditedEntity)
CREATE TABLE IF NOT EXISTS teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    code VARCHAR(255),
    effectif INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255)
);

-- 3. Roles table
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- 4. Telework Requests table (DemandeTeletravail + TeleworkRequest + AuditedEntity)
CREATE TABLE IF NOT EXISTS telework_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    alfresco_node_id VARCHAR(255),
    agreement_node_id VARCHAR(255),
    status VARCHAR(255), -- Denormalized status used in early logic
    decision_comment VARCHAR(1000),
    process_instance_id VARCHAR(255),
    reference_code VARCHAR(255) UNIQUE,
    date_creation DATETIME NOT NULL,
    type VARCHAR(255),
    utilisateur_id BIGINT,
    equipe_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255),
    CONSTRAINT fk_telework_user FOREIGN KEY (utilisateur_id) REFERENCES users(id),
    CONSTRAINT fk_telework_team FOREIGN KEY (equipe_id) REFERENCES teams(id)
);

-- 5. Business Configurations table
CREATE TABLE IF NOT EXISTS business_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cle VARCHAR(255) NOT NULL UNIQUE,
    valeur VARCHAR(255) NOT NULL,
    date_modification DATETIME NOT NULL
);

-- 6. Scoring Equipes table
CREATE TABLE IF NOT EXISTS scoring_equipes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    periode VARCHAR(255) NOT NULL,
    taux_presence DOUBLE,
    taux_teletravail DOUBLE,
    score_global DOUBLE,
    team_id BIGINT NOT NULL,
    CONSTRAINT fk_scoring_team FOREIGN KEY (team_id) REFERENCES teams(id)
);

-- 7. Audit Logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    entite VARCHAR(255) NOT NULL,
    event_timestamp DATETIME NOT NULL,
    utilisateur VARCHAR(255),
    demande_id BIGINT,
    workflow_task_id BIGINT,
    CONSTRAINT fk_audit_demande_v0 FOREIGN KEY (demande_id) REFERENCES telework_requests(id)
);

-- 8. Justificatifs table
CREATE TABLE IF NOT EXISTS justificatifs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(255),
    type VARCHAR(255),
    node_id VARCHAR(255),
    demande_id BIGINT,
    CONSTRAINT fk_justif_demande_v0 FOREIGN KEY (demande_id) REFERENCES telework_requests(id)
);

-- 9. Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message TEXT,
    destinataire VARCHAR(255),
    date_envoi DATETIME,
    demande_id BIGINT,
    CONSTRAINT fk_notif_demande_v0 FOREIGN KEY (demande_id) REFERENCES telework_requests(id)
);

-- 10. Workflow Instances / Tasks
CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_instance_id VARCHAR(255),
    current_step VARCHAR(255),
    demande_id BIGINT UNIQUE,
    CONSTRAINT fk_workflow_demande_v0 FOREIGN KEY (demande_id) REFERENCES telework_requests(id)
);

CREATE TABLE IF NOT EXISTS workflow_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255),
    task_name VARCHAR(255),
    assignee VARCHAR(255),
    demande_id BIGINT,
    CONSTRAINT fk_task_demande_v0 FOREIGN KEY (demande_id) REFERENCES telework_requests(id)
);

-- 11. Rapports table
CREATE TABLE IF NOT EXISTS rapports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    periode VARCHAR(255) NOT NULL,
    date_generation DATETIME NOT NULL,
    scoring_equipe_id BIGINT NOT NULL,
    CONSTRAINT fk_rapport_scoring FOREIGN KEY (scoring_equipe_id) REFERENCES scoring_equipes(id)
);
