-- ===============================================================
-- Migration: Add wayBillId column to shipments table
-- Database: beyos_delivery
-- Purpose: Store Koombiyo waybill ID for courier tracking
-- ===============================================================

USE beyos_delivery;

-- Add wayBillId column to shipments table (only if it doesn't exist)
SET @dbname = DATABASE();
SET @tablename = 'shipments';
SET @columnname = 'way_bill_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE (TABLE_NAME = @tablename)
   AND (COLUMN_NAME = @columnname)
   AND (TABLE_SCHEMA = @dbname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " VARCHAR(100) NULL COMMENT 'Koombiyo waybill ID (barcode)' AFTER tracking_number")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Create index for faster lookups
SET @indexname = 'idx_shipment_way_bill_id';
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
