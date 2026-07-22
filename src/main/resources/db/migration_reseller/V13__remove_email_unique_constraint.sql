
ALTER TABLE resellers DROP INDEX email;

ALTER TABLE resellers MODIFY COLUMN email VARCHAR(255) NOT NULL COMMENT 'Used for login - not unique to allow reusing deactivated emails';
