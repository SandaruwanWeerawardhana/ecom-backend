-- =====================================================
-- Table: payment_method_fees
-- Purpose: Additional fees by customer type (CUSTOMER vs RESELLER)
-- =====================================================

USE beyos_payment;

CREATE TABLE payment_method_fees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    method_id BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    customer_type ENUM('CUSTOMER','RESELLER') NOT NULL,
    fee_type ENUM('FIXED','PERCENTAGE') DEFAULT 'FIXED',
    fee_value DECIMAL(10,2) NOT NULL,
    is_free_shipping BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (method_id) REFERENCES payment_methods(id) ON DELETE CASCADE,
    INDEX idx_method_customer (method_id, customer_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

