-- Repair users table to match domain entity (Utilisateur/User)
-- Mapping team association (team_id instead of equipe_id if the join column is team_id)
ALTER TABLE users ADD COLUMN IF NOT EXISTS team_id BIGINT;

SET @dbname = DATABASE();

SET @users_team_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'team_id'
      AND REFERENCED_TABLE_NAME = 'teams'
      AND REFERENCED_COLUMN_NAME = 'id'
);

SET @users_team_orphans = (
    SELECT COUNT(*)
    FROM users u
    LEFT JOIN teams t ON t.id = u.team_id
    WHERE u.team_id IS NOT NULL
      AND t.id IS NULL
);

SET @add_users_team_fk_query = IF(
    @users_team_fk_exists = 0 AND @users_team_orphans = 0,
    'ALTER TABLE users ADD CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams(id)',
    'SELECT 1'
);
PREPARE stmt_add_users_team_fk FROM @add_users_team_fk_query;
EXECUTE stmt_add_users_team_fk;
DEALLOCATE PREPARE stmt_add_users_team_fk;
