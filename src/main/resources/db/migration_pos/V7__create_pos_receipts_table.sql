-- =====================================================
-- Table: pos_receipts
-- Purpose: Track printed receipts history
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public receipt UUID',
    order_id BIGINT NOT NULL COMMENT 'Reference to order (beyos_order.orders.id)',
    receipt_number VARCHAR(50) UNIQUE NOT NULL COMMENT 'Format: RCP-YYYYMMDD-XXXX',
    print_count INT NOT NULL DEFAULT 1 COMMENT 'Number of times printed',
    printed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'First print time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_order_id (order_id),
    INDEX idx_receipt_number (receipt_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='POS receipt printing history';

