-- STEP 4 & 5 & 6 — Repair telework_requests schema

-- Ensure state column exists and match enum
-- First add as VARCHAR if not exists to be safe
ALTER TABLE telework_requests
ADD COLUMN IF NOT EXISTS state VARCHAR(255);

-- Then modify to ENUM to match requirements
ALTER TABLE telework_requests
MODIFY COLUMN state ENUM(
    'SUBMITTED',
    'APPROVED',
    'REJECTED',
    'SPECIAL'
) NOT NULL DEFAULT 'SUBMITTED';

-- Backfill existing rows for state
UPDATE telework_requests
SET state='SUBMITTED'
WHERE state IS NULL;

-- Handle legacy status column (STEP 5)
-- Assuming 'status' exists based on V1 having an index on it
UPDATE telework_requests
SET state=status
WHERE state = 'SUBMITTED'
AND status IS NOT NULL;

-- Remove legacy column safely
ALTER TABLE telework_requests
DROP COLUMN IF EXISTS status;

-- Add NOT NULL constraints (STEP 6)
ALTER TABLE telework_requests
MODIFY COLUMN employee_id VARCHAR(255) NOT NULL;

ALTER TABLE telework_requests
MODIFY COLUMN start_date DATE NOT NULL;

ALTER TABLE telework_requests
MODIFY COLUMN end_date DATE NOT NULL;
