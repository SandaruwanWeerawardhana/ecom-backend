-- ============================================
-- VERIFICATION SCRIPT FOR PERMISSIONS AND ROLES
-- Run this to check the current state
-- ============================================

USE beyos_auth_db;

-- 1. Check if permissions exist
SELECT 'PERMISSIONS COUNT:' as check_type, COUNT(*) as count FROM user_permission;
SELECT 'PARENT PERMISSIONS:' as type, permission_code, permission_name FROM user_permission WHERE is_parent = true ORDER BY display_order;
SELECT 'CHILD PERMISSIONS:' as type, permission_code, permission_name FROM user_permission WHERE is_parent = false ORDER BY permission_code;

-- 2. Check if roles exist
SELECT 'ROLES COUNT:' as check_type, COUNT(*) as count FROM user_role;
SELECT 'ROLES:' as type, role_code, role_name, user_type FROM user_role;

-- 3. Check role-permission mappings
SELECT 'ROLE PERMISSIONS COUNT:' as check_type, COUNT(*) as count FROM role_permission;
SELECT
    ur.role_code,
    ur.role_name,
    up.permission_code,
    up.permission_name
FROM role_permission rp
JOIN user_role ur ON rp.role_id = ur.id
JOIN user_permission up ON rp.permission_id = up.id
WHERE rp.is_active = true
ORDER BY ur.role_code, up.permission_code;

-- 4. Check SUPER_ADMIN role permissions specifically
SELECT
    'SUPER_ADMIN PERMISSIONS:' as type,
    COUNT(*) as permission_count
FROM role_permission rp
JOIN user_role ur ON rp.role_id = ur.id
WHERE ur.role_code = 'SUPER_ADMIN' AND rp.is_active = true;

-- 5. Check admin user
SELECT 'ADMIN USER:' as type, id, username, email, user_type, user_role_id, email_verified FROM users WHERE email = 'akiyaramsith2002@gmail.com';

-- 6. If admin user exists, show their permissions
SELECT
    u.email,
    ur.role_code,
    up.permission_code
FROM users u
LEFT JOIN user_role ur ON u.user_role_id = ur.id
LEFT JOIN role_permission rp ON rp.role_id = ur.id AND rp.is_active = true
LEFT JOIN user_permission up ON rp.permission_id = up.id
WHERE u.email = 'akiyaramsith2002@gmail.com'
ORDER BY up.permission_code;

