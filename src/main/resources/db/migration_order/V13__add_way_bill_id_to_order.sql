-- ===============================================================
-- Migration: Add wayBillId column to orders table
-- Database: beyos_order
-- Purpose: Store Koombiyo waybill ID for courier tracking
-- ===============================================================

USE beyos_order;

-- Add wayBillId column to orders table (only if it doesn't exist)
SET @dbname = DATABASE();
SET @tablename = 'orders';
SET @columnname = 'way_bill_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE (TABLE_NAME = @tablename)
   AND (COLUMN_NAME = @columnname)
   AND (TABLE_SCHEMA = @dbname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " VARCHAR(100) NULL COMMENT 'Koombiyo waybill ID (barcode)' AFTER order_number")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Create index for faster lookups (only if it doesn't exist)
SET @indexname = 'idx_way_bill_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname
   AND TABLE_SCHEMA = @dbname
  ) > 0,
  "SELECT 1",
  CONCAT("CREATE INDEX ", @indexname, " ON ", @tablename, "(way_bill_id)")
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;