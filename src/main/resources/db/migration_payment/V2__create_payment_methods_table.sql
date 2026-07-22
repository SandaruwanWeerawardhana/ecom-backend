-- =====================================================
-- Table: payment_methods
-- Purpose: Global list of all payment methods (Online/Offline/POS)
-- =====================================================

USE beyos_payment;

CREATE TABLE payment_methods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT 'Display name: OnePay, Cash on Delivery, POS Card',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT 'Unique code: onepay, cod, pos_card, pos_cash',
    type ENUM('ONLINE','OFFLINE','POS') NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    supports_refund BOOLEAN DEFAULT FALSE,
    supports_callback BOOLEAN DEFAULT FALSE,
    is_courier_fee_free BOOLEAN DEFAULT FALSE COMMENT 'If true, shipping is free with this payment method',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_code (code),
    INDEX idx_type (type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

