-- ========================================
-- UPDATE AUTH DATABASE - USER TABLE
-- MySQL 5.7 Compatible Migration
-- ========================================

-- This migration ensures data consistency
-- The users table in V1 already has user_type column and idx_user_type index
-- This is a safety migration for data cleanup only

-- Update any null user_type values (safety check)
UPDATE users
SET user_type = CASE
    WHEN user_role_id IS NOT NULL THEN 'ADMIN'
    ELSE 'CUSTOMER'
END
WHERE user_type IS NULL OR user_type = '';

-- Ensure user_type is NOT NULL (safe to re-run)
ALTER TABLE users MODIFY user_type VARCHAR(50) NOT NULL COMMENT 'ADMIN, CUSTOMER, RESELLER';

-- Note: Index already exists from V1 migration
-- This migration only ensures data consistency
