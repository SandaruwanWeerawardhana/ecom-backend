-- =====================================================
-- COMPLETE FIX FOR payment_method_fees TABLE
-- Run this script manually in your MySQL client
-- =====================================================

USE beyos_payment;

-- Step 1: Remove the failed migration from Flyway schema history
DELETE FROM flyway_schema_history WHERE version = '12' AND success = 0;

-- Step 2: Add uuid column if missing
SET @column_exists = (SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'beyos_payment'
    AND TABLE_NAME = 'payment_method_fees'
    AND COLUMN_NAME = 'uuid');

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE payment_method_fees ADD COLUMN uuid CHAR(36) UNIQUE AFTER id',
    'SELECT "uuid column already exists" AS status');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 3: Add is_active column if missing
SET @column_exists = (SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'beyos_payment'
    AND TABLE_NAME = 'payment_method_fees'
    AND COLUMN_NAME = 'is_active');

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE payment_method_fees ADD COLUMN is_active BOOLEAN DEFAULT TRUE AFTER method_id',
    'SELECT "is_active column already exists" AS status');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 4: Update NULL uuid values with generated UUIDs
UPDATE payment_method_fees
SET uuid = UUID()
WHERE uuid IS NULL OR uuid = '';

-- Step 5: Make uuid NOT NULL if it's nullable
SET @is_nullable = (SELECT IS_NULLABLE
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'beyos_payment'
    AND TABLE_NAME = 'payment_method_fees'
    AND COLUMN_NAME = 'uuid');

SET @sql = IF(@is_nullable = 'YES',
    'ALTER TABLE payment_method_fees MODIFY COLUMN uuid CHAR(36) UNIQUE NOT NULL',
    'SELECT "uuid column already NOT NULL" AS status');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Step 6: Verify the fix
SELECT
    'Column verification:' AS step,
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM
    INFORMATION_SCHEMA.COLUMNS
WHERE
    TABLE_SCHEMA = 'beyos_payment'
    AND TABLE_NAME = 'payment_method_fees'
    AND COLUMN_NAME IN ('uuid', 'is_active')
ORDER BY ORDINAL_POSITION;

SELECT
    'Flyway history check:' AS step,
    COUNT(*) as failed_migrations
FROM
    flyway_schema_history
WHERE
    version = '12' AND success = 0;

SELECT 'Fix completed successfully! You can now restart your application.' AS status;

