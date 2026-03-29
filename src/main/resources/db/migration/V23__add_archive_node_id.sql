-- V23: Add archive_node_id column to telework_requests for Alfresco PDF archive reference
ALTER TABLE telework_requests
    ADD COLUMN IF NOT EXISTS archive_node_id VARCHAR(255) NULL;
