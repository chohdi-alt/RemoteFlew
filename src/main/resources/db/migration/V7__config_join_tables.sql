-- Create join tables for business configurations
CREATE TABLE IF NOT EXISTS configuration_telework_request (
    configuration_id BIGINT NOT NULL,
    telework_request_id BIGINT NOT NULL,
    PRIMARY KEY (configuration_id, telework_request_id),
    CONSTRAINT fk_config_telework_config FOREIGN KEY (configuration_id) REFERENCES business_configurations (id) ON DELETE CASCADE,
    CONSTRAINT fk_config_telework_request FOREIGN KEY (telework_request_id) REFERENCES telework_requests (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS configuration_scoring_equipe (
    configuration_id BIGINT NOT NULL,
    scoring_equipe_id BIGINT NOT NULL,
    PRIMARY KEY (configuration_id, scoring_equipe_id),
    CONSTRAINT fk_config_scoring_config FOREIGN KEY (configuration_id) REFERENCES business_configurations (id) ON DELETE CASCADE,
    CONSTRAINT fk_config_scoring_equipe FOREIGN KEY (scoring_equipe_id) REFERENCES scoring_equipes (id) ON DELETE CASCADE
);
