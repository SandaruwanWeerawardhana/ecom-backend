-- ============================================
-- Remove old dummy permissions and prepare for hierarchical permissions
-- Migration: V3__remove_old_permissions.sql
-- ============================================

-- Delete old role-permission mappings
DELETE FROM role_permission;

-- Delete old dummy permissions
DELETE FROM user_permission
WHERE permission_code IN (
    'USER_READ', 'USER_WRITE', 'USER_DELETE',
    'PRODUCT_READ', 'PRODUCT_WRITE', 'PRODUCT_DELETE',
    'ORDER_READ', 'ORDER_WRITE', 'ORDER_DELETE',
    'ADMIN_ACCESS'
);

-- Note: The hierarchical permissions will be initialized by the application
-- via the initializePermissions() method when triggered by RabbitMQ event
-- or API endpoint

