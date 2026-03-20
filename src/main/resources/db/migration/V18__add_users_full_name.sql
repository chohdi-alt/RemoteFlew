-- V18__add_users_full_name.sql
-- Ensure users.full_name exists and is populated for Hibernate validation

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);

UPDATE users
SET full_name = CASE
    WHEN full_name IS NOT NULL AND TRIM(full_name) <> '' THEN TRIM(full_name)
    ELSE NULLIF(TRIM(CONCAT(IFNULL(prenom, ''), ' ', IFNULL(nom, ''))), '')
END;

UPDATE users
SET full_name = 'N/A'
WHERE full_name IS NULL OR TRIM(full_name) = '';

ALTER TABLE users
    MODIFY COLUMN full_name VARCHAR(255) NOT NULL;
