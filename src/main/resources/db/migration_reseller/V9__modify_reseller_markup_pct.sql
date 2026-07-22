-- ============================================
-- Migration: V9__modify_reseller_markup_pct.sql
-- Description: Modify reseller markup percentage fields to DECIMAL(15,2) for greater precision
-- ============================================
-- ALTER TABLE resellers
--     MODIFY COLUMN `min_allowed_markup_pct` `min_allowed_markup_pct` DECIMAL(15,2) NULL DEFAULT NULL ,
--     CHANGE COLUMN `max_allowed_markup_pct` `max_allowed_markup_pct` DECIMAL(15,2) NULL DEFAULT NULL ;

ALTER TABLE resellers
    MODIFY COLUMN `min_allowed_markup_pct` DECIMAL(15,2) NULL DEFAULT NULL,
    MODIFY COLUMN `max_allowed_markup_pct` DECIMAL(15,2) NULL DEFAULT NULL;
