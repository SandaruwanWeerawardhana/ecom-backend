-- =====================================================
-- Table: pos_carts
-- Purpose: Live POS transactions before checkout
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public cart UUID',
    terminal_id BIGINT NOT NULL COMMENT 'FK to pos_terminals',
    cashier_id BIGINT NOT NULL COMMENT 'FK to pos_cashiers',
    customer_id BIGINT NULL COMMENT 'FK to customer (NULL for walk-in customers)',
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Sum of all items before tax/discount',
    tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Calculated tax amount',
    tax_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT 'Tax rate applied (e.g., 15.00)',
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Flat discount applied',
    total DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Final total = subtotal + tax - discount',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Cart active status (one per terminal)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_terminal_id (terminal_id),
    INDEX idx_cashier_id (cashier_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_is_active (is_active),

    CONSTRAINT fk_pos_carts_terminal FOREIGN KEY (terminal_id)
        REFERENCES pos_terminals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pos_carts_cashier FOREIGN KEY (cashier_id)
        REFERENCES pos_cashiers(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Active POS carts for ongoing transactions';

