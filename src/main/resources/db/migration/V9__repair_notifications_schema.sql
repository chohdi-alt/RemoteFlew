-- Repair notifications table to match domain entity
ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS type VARCHAR(255);

ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS lue BOOLEAN NOT NULL DEFAULT FALSE;

-- Ensure date_envoi is mandatory
UPDATE notifications SET date_envoi = CURRENT_TIMESTAMP WHERE date_envoi IS NULL;
ALTER TABLE notifications MODIFY COLUMN date_envoi DATETIME NOT NULL;

-- Ensure message length matches entity
ALTER TABLE notifications MODIFY COLUMN message VARCHAR(1000) NOT NULL;
