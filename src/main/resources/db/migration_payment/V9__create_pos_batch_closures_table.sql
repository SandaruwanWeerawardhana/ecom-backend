-- =====================================================
-- Table: pos_batch_closures
-- Purpose: Daily POS settlement/batch closure tracking
-- =====================================================

USE beyos_payment;

CREATE TABLE pos_batch_closures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    terminal_id VARCHAR(50) NOT NULL,
    cashier_id BIGINT NULL,
    total_cash DECIMAL(10,2) DEFAULT 0.00,
    total_card DECIMAL(10,2) DEFAULT 0.00,
    total_orders INT DEFAULT 0,
    opened_at DATETIME NOT NULL,
    closed_at DATETIME NULL,

    INDEX idx_terminal_id (terminal_id),
    INDEX idx_cashier_id (cashier_id),
    INDEX idx_opened_at (opened_at),
    INDEX idx_closed_at (closed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

