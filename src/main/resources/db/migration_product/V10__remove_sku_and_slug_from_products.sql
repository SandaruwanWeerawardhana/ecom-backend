-- ============================================
-- Remove UNIQUE constraints from sku and slug columns in products table
-- Migration: V10__remove_sku_and_slug_from_products.sql
-- ============================================

DROP PROCEDURE IF EXISTS drop_index_if_exists;

DELIMITER $$
CREATE PROCEDURE drop_index_if_exists(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl
      AND INDEX_NAME = idx
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', tbl, '` DROP INDEX `', idx, '`');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
END IF;
END$$
DELIMITER ;

CALL drop_index_if_exists('products', 'sku');
CALL drop_index_if_exists('products', 'slug');

DROP PROCEDURE IF EXISTS drop_index_if_exists;