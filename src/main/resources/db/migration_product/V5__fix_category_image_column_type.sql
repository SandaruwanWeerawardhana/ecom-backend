-- ============================================
-- PRODUCT DATABASE MIGRATION
-- Migration: V5__fix_category_image_column_type.sql
-- Description: Fix category_image column to use LONGTEXT instead of VARCHAR
-- ============================================
--
-- -- Add category_image column if it doesn't exist (for databases that never had it)
-- ALTER TABLE product_categories
--     ADD COLUMN IF NOT EXISTS category_image LONGTEXT NULL COMMENT 'S3 object key or public/presigned URL for the category image';

-- Modify existing column to LONGTEXT (for databases that have it as VARCHAR)
ALTER TABLE product_categories
    MODIFY COLUMN category_image LONGTEXT NULL COMMENT 'S3 object key or public/presigned URL for the category image';

