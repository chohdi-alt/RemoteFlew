-- Repair justificatifs table to match domain entity
ALTER TABLE justificatifs
ADD COLUMN IF NOT EXISTS date_ajout TIMESTAMP NULL;

ALTER TABLE justificatifs
ADD COLUMN IF NOT EXISTS alfresco_node_id VARCHAR(255);

ALTER TABLE justificatifs
ADD COLUMN IF NOT EXISTS nom_fichier VARCHAR(255);

SET @dbname = DATABASE();

-- Backfill from legacy file-name column when available (nom -> nom_fichier)
SELECT COUNT(*) INTO @justif_has_nom
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname
  AND TABLE_NAME = 'justificatifs'
  AND COLUMN_NAME = 'nom';

SET @copy_nom_query = IF(
    @justif_has_nom > 0,
    'UPDATE justificatifs SET nom_fichier = nom WHERE nom_fichier IS NULL AND nom IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_copy_nom FROM @copy_nom_query;
EXECUTE stmt_copy_nom;
DEALLOCATE PREPARE stmt_copy_nom;

-- Backfill from legacy Alfresco node column when available (node_id -> alfresco_node_id)
SELECT COUNT(*) INTO @justif_has_node_id
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @dbname
  AND TABLE_NAME = 'justificatifs'
  AND COLUMN_NAME = 'node_id';

SET @copy_node_query = IF(
    @justif_has_node_id > 0,
    'UPDATE justificatifs SET alfresco_node_id = node_id WHERE alfresco_node_id IS NULL AND node_id IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_copy_node FROM @copy_node_query;
EXECUTE stmt_copy_node;
DEALLOCATE PREPARE stmt_copy_node;

-- Ensure mandatory values are present before enforcing NOT NULL
UPDATE justificatifs
SET nom_fichier = CONCAT('justificatif-', id)
WHERE nom_fichier IS NULL;

UPDATE justificatifs
SET date_ajout = CURRENT_TIMESTAMP
WHERE date_ajout IS NULL;

-- Final constraints expected by JPA entity
ALTER TABLE justificatifs MODIFY COLUMN nom_fichier VARCHAR(255) NOT NULL;
ALTER TABLE justificatifs MODIFY COLUMN date_ajout TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
