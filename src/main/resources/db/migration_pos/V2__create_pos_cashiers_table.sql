-- =====================================================
-- Table: pos_cashiers
-- Purpose: Staff members who operate POS terminals
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_cashiers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public cashier UUID',
    user_id BIGINT NULL COMMENT 'FK to admin users (NULL for standalone cashiers)',
    name VARCHAR(100) NOT NULL COMMENT 'Cashier full name',
    pin_code VARCHAR(255) NULL COMMENT 'Optional encrypted PIN for quick login',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Cashier active status',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_user_id (user_id),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='POS cashiers and staff members';

