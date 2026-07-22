-- V7__create_reseller_wallet_transactions_table.sql
-- Create reseller_wallet_transactions table for wallet transaction tracking

CREATE TABLE reseller_wallet_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    reseller_id BIGINT NOT NULL,
    type ENUM('SALE_PROFIT', 'WITHDRAWAL_DEBIT', 'WITHDRAWAL_REVERSED', 'ADMIN_CREDIT', 'ADMIN_DEBIT', 'ORDER_REFUND') NOT NULL,
    amount DECIMAL(12,2) NOT NULL COMMENT 'Positive for credit, negative for debit',
    balance_before DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    reference_order_id BIGINT COMMENT 'If related to an order',
    reference_withdrawal_id BIGINT COMMENT 'If related to a withdrawal',
    notes TEXT,
    created_by_admin_id BIGINT COMMENT 'If admin action',
    date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_reseller_wallet_transactions_uuid (uuid),
    INDEX idx_reseller_wallet_transactions_reseller_id (reseller_id),
    INDEX idx_reseller_wallet_transactions_type (type),
    INDEX idx_reseller_wallet_transactions_date_created (date_created),
    INDEX idx_reseller_wallet_transactions_reference_order_id (reference_order_id),
    INDEX idx_reseller_wallet_transactions_reseller_type (reseller_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

