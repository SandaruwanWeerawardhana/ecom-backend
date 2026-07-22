-- =====================================================
-- Table: pos_shifts
-- Purpose: Cashier shift tracking and cash reconciliation
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_shifts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public shift UUID',
    cashier_id BIGINT NOT NULL COMMENT 'FK to pos_cashiers',
    terminal_id BIGINT NOT NULL COMMENT 'FK to pos_terminals',
    opened_at DATETIME NOT NULL COMMENT 'Shift start time',
    closed_at DATETIME NULL COMMENT 'Shift end time (NULL = active shift)',
    opening_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Starting cash in drawer',
    closing_balance DECIMAL(10,2) NULL COMMENT 'Ending cash counted',
    expected_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'opening + cash sales',
    difference DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'closing - expected (over/short)',
    notes TEXT NULL COMMENT 'Reconciliation notes',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_cashier_id (cashier_id),
    INDEX idx_terminal_id (terminal_id),
    INDEX idx_opened_at (opened_at),

    CONSTRAINT fk_pos_shifts_cashier FOREIGN KEY (cashier_id)
        REFERENCES pos_cashiers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pos_shifts_terminal FOREIGN KEY (terminal_id)
        REFERENCES pos_terminals(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='POS shift management and cash drawer reconciliation';

