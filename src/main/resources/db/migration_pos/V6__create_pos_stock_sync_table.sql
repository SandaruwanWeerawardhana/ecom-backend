-- =====================================================
-- Table: pos_stock_sync
-- Purpose: Track inventory synchronization for POS sales
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_stock_sync (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public sync record UUID',
    pos_order_id BIGINT NOT NULL COMMENT 'Reference to order ID',
    product_id BIGINT NOT NULL COMMENT 'Product whose stock was deducted',
    variant_id BIGINT NULL COMMENT 'Variant ID if applicable',
    quantity INT NOT NULL COMMENT 'Quantity deducted',
    synced BOOLEAN DEFAULT FALSE COMMENT 'Whether inventory updated',
    synced_at DATETIME NULL COMMENT 'When sync completed',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_pos_order_id (pos_order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_synced (synced)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit trail for POS inventory synchronization';

