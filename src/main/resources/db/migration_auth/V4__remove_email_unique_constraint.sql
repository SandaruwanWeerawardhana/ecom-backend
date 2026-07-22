

ALTER TABLE users DROP INDEX email;

-- Modify the column to remove unique property (already handled by DROP INDEX above)
ALTER TABLE users MODIFY COLUMN email VARCHAR(255) NOT NULL COMMENT 'Used for login - not unique to allow reusing deactivated emails';

-- Verify: The regular index idx_email still exists for performance
-- No need to add it again as it was created in V1 migration
