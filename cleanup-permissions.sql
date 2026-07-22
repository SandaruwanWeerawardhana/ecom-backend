-- ============================================
-- CLEANUP PERMISSIONS - Run this in MySQL Workbench
-- This will delete all existing permissions so you can re-initialize them
-- ============================================

USE beyos_auth_db;

-- First, delete all role-permission mappings
DELETE FROM role_permission;

-- Then delete all permissions (both parent and child)
DELETE FROM user_permission;

-- Reset auto-increment
ALTER TABLE user_permission AUTO_INCREMENT = 1;
ALTER TABLE role_permission AUTO_INCREMENT = 1;

-- Verify deletion
SELECT COUNT(*) as remaining_permissions FROM user_permission;
SELECT COUNT(*) as remaining_role_permissions FROM role_permission;

-- ============================================
-- After running this script:
-- 1. Restart your Spring Boot application
-- 2. Call the permission initialization endpoint or trigger the event
-- 3. The permissions will be re-created with correct parent_id values
-- ============================================

