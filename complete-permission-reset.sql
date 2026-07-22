-- ============================================
-- COMPLETE PERMISSION CLEANUP AND RESET
-- Run this script to remove old permissions and prepare for initialization
-- ============================================

USE beyos_auth_db;

-- Step 1: Delete all role-permission mappings
DELETE FROM role_permission;
SELECT 'Step 1: Deleted role-permission mappings' AS status;

-- Step 2: Delete all permissions (including old dummy ones)
DELETE FROM user_permission;
SELECT 'Step 2: Deleted all permissions' AS status;

-- Step 3: Reset auto-increment
ALTER TABLE user_permission AUTO_INCREMENT = 1;
ALTER TABLE role_permission AUTO_INCREMENT = 1;
SELECT 'Step 3: Reset auto-increment counters' AS status;

-- Step 4: Verify cleanup
SELECT 'Permissions remaining:' AS status, COUNT(*) as count FROM user_permission;
SELECT 'Role-permissions remaining:' AS status, COUNT(*) as count FROM role_permission;

-- ============================================
-- Next Steps:
-- 1. Run this script in MySQL Workbench
-- 2. Restart your Spring Boot application
-- 3. Call the API endpoint: POST http://localhost:8080/api/admin/permissions/initialize
-- 4. Check the logs for "✅ Permissions initialized successfully"
-- 5. Verify in MySQL Workbench that hierarchical permissions are created
-- ============================================

