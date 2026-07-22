-- =====================================================
-- Table: courier_payment_additional_fees
-- Purpose: Additional fees per payment method (e.g., COD fee)
-- =====================================================

USE beyos_delivery;

CREATE TABLE courier_payment_additional_fees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    courier_id BIGINT NOT NULL,
    payment_method_id BIGINT NOT NULL,
    fee_type ENUM('FIXED','PERCENT') DEFAULT 'FIXED',
    fee_value DECIMAL(10,2) NOT NULL COMMENT 'Amount or percentage value',
    applies_to_customer_type ENUM('CUSTOMER','RESELLER','BOTH') DEFAULT 'BOTH',
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (courier_id) REFERENCES couriers(id) ON DELETE CASCADE,
    INDEX idx_courier_payment (courier_id, payment_method_id),
    INDEX idx_customer_type (applies_to_customer_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

