-- =====================================================
-- Table: pos_payments
-- Purpose: POS-specific payment details (card last 4, terminal, approval code)
-- =====================================================

USE beyos_payment;

CREATE TABLE pos_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL,
    method_id BIGINT NOT NULL COMMENT 'FK to payment_methods (pos_cash, pos_card)',
    amount DECIMAL(10,2) NOT NULL,
    pos_terminal VARCHAR(100) NULL COMMENT 'Terminal identifier',
    cashier_id BIGINT NULL COMMENT 'Staff user ID',
    card_last4 VARCHAR(10) NULL COMMENT 'Last 4 digits of card (for pos_card)',
    card_type VARCHAR(50) NULL COMMENT 'VISA, MASTERCARD, AMEX',
    approval_code VARCHAR(50) NULL COMMENT 'POS machine approval code',
    reference_id VARCHAR(100) NULL COMMENT 'Receipt ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (method_id) REFERENCES payment_methods(id),
    INDEX idx_order_id (order_id),
    INDEX idx_method_id (method_id),
    INDEX idx_pos_terminal (pos_terminal),
    INDEX idx_cashier_id (cashier_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

