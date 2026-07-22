-- ============================================
-- PROMOTION MODULE PERMISSIONS
-- Insert promotion-related permissions into admin permissions table
-- ============================================

-- Use the admin database
USE beyos_admin_db;

-- Insert Promotion Module Permissions
INSERT INTO permissions (uuid, code, name, description, module, is_active, date_created, date_updated) VALUES
(UUID(), 'CREATE_PROMOTION', 'Create Promotion', 'Permission to create new promotions and discounts', 'PROMOTION', TRUE, NOW(), NOW()),
(UUID(), 'VIEW_PROMOTION', 'View Promotion', 'Permission to view promotions and discounts', 'PROMOTION', TRUE, NOW(), NOW()),
(UUID(), 'EDIT_PROMOTION', 'Edit Promotion', 'Permission to edit existing promotions and discounts', 'PROMOTION', TRUE, NOW(), NOW()),
(UUID(), 'DELETE_PROMOTION', 'Delete Promotion', 'Permission to delete promotions and discounts', 'PROMOTION', TRUE, NOW(), NOW()),
(UUID(), 'MANAGE_PROMOTIONS', 'Manage Promotions', 'Full permission to manage all promotions', 'PROMOTION', TRUE, NOW(), NOW());

-- Assign Promotion Permissions to SUPER_ADMIN role
INSERT INTO role_permissions (role_id, permission_id, date_created)
SELECT r.id, p.id, NOW()
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
AND p.module = 'PROMOTION'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- Verify permissions were created
SELECT
    p.code,
    p.name,
    p.module,
    p.description
FROM permissions p
WHERE p.module = 'PROMOTION'
ORDER BY p.code;

-- Verify SUPER_ADMIN has promotion permissions
SELECT
    r.name AS role_name,
    p.code AS permission_code,
    p.name AS permission_name
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE r.code = 'SUPER_ADMIN' AND p.module = 'PROMOTION'
ORDER BY p.code;

