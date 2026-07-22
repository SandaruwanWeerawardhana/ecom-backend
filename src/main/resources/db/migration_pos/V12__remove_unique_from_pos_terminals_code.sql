-- Use your DB
USE beyos_pos;

-- Find a UNIQUE index that is single-column on `code` (so we don't drop composite unique indexes)
SELECT @unique_index := INDEX_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'pos_terminals'
  AND COLUMN_NAME = 'code'
  AND NON_UNIQUE = 0
GROUP BY INDEX_NAME
HAVING COUNT(*) = 1
LIMIT 1;

-- If found, drop it; otherwise do nothing (harmless select)
SET @drop_sql = IF(@unique_index IS NULL,
                   'SELECT \"no_single_column_unique_index_found\"',
                   CONCAT('ALTER TABLE `pos_terminals` DROP INDEX `', @unique_index, '`'));

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Ensure there is at least one index on `code` (non-unique). If none, create idx_code.
SELECT @has_index := COUNT(*)
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()