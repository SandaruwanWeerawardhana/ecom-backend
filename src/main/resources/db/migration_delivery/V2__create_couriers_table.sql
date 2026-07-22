-- =====================================================
-- Table: couriers
-- Purpose: Store courier company information and API details
-- =====================================================

USE beyos_delivery;

CREATE TABLE couriers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT 'Unique code e.g. DHL_LK, LOCAL_EXPRESS',
    name VARCHAR(255) NOT NULL COMMENT 'Display name e.g. FastLocal Courier',
    description VARCHAR(500) NULL,
    api_base_url VARCHAR(1024) NULL COMMENT 'Base URL for courier API (tracking/booking)',
    api_key VARCHAR(1024) NULL COMMENT 'API key (encrypt in production)',
    contact_phone VARCHAR(50) NULL,
    email VARCHAR(255) NULL,
    is_active BOOLEAN DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_code (code),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
