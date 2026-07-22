-- =====================================================
-- Table: payment_method_config
-- Purpose: Store gateway credentials (API keys, secrets, etc.)
-- =====================================================

USE beyos_payment;

CREATE TABLE payment_method_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    method_id BIGINT NOT NULL,
    label VARCHAR(100) NOT NULL COMMENT 'Config label: API Key, App ID, Secret Key',
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NOT NULL,
    is_secret BOOLEAN DEFAULT TRUE COMMENT 'If true, should be encrypted/masked',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (method_id) REFERENCES payment_methods(id) ON DELETE CASCADE,
    INDEX idx_method_id (method_id),
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

