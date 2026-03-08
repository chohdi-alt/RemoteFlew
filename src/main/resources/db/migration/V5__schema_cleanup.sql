-- STEP 1: DROP INVALID/OBSOLETE INDEXES
-- Drop index that references removed `status` column. (We use IF EXISTS if MariaDB supports it)
DROP INDEX IF EXISTS idx_telework_status ON telework_requests;

-- Drop index that references non-existent `created_at` column.
DROP INDEX IF EXISTS idx_telework_created_at ON telework_requests;

-- STEP 2: ENSURE ALFRESCO INTEGRATION REMAINS OPTIONAL
-- These columns are already optional (since they don't have NOT NULL constraints),
-- but we explicitly define their nullable state to guarantee the schema definition matches intent.
ALTER TABLE telework_requests
    MODIFY COLUMN alfresco_node_id VARCHAR(255) NULL;

ALTER TABLE telework_requests
    MODIFY COLUMN agreement_node_id VARCHAR(255) NULL;

-- STEP 3: IMPROVE QUERY PERFORMANCE FOR STATUS FILTERING
-- Create index on the current `state` column (used heavily in the application)
CREATE INDEX IF NOT EXISTS idx_telework_state ON telework_requests (state);
