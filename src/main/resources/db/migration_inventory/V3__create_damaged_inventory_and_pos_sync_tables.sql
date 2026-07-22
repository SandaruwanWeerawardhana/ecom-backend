-- ============================================
-- INVENTORY DATABASE SCHEMA - beyos_inventory_db
-- Migration: V3__create_damaged_inventory_and_pos_sync_tables.sql
-- ============================================

-- ========================================
-- Damaged Inventory Table
-- ========================================
CREATE TABLE IF NOT EXISTS damaged_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL COMMENT 'References products.id from product module',
    variant_id BIGINT NULL COMMENT 'References product_variants.id from product module',
    quantity INT NOT NULL,
    reason TEXT NOT NULL,
    source ENUM('CUSTOMER_RETURN', 'WAREHOUSE_DAMAGE', 'POS_DAMAGE') NOT NULL,
    inspected_by BIGINT NULL COMMENT 'Staff/admin who marked it damaged',
    reference_return_id BIGINT NULL COMMENT 'If from return, link to return record',
    reference_order_id BIGINT NULL COMMENT 'If related to order, link to order',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_source (source),
    INDEX idx_inspected_by (inspected_by),
    INDEX idx_date_created (date_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- POS Stock Sync Logs Table
-- ========================================
CREATE TABLE IF NOT EXISTS pos_stock_sync_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL COMMENT 'References products.id from product module',
    variant_id BIGINT NULL COMMENT 'References product_variants.id from product module',
    pos_change INT NOT NULL COMMENT 'Stock change amount: -3 sold, +10 restocked, etc.',
    sync_status ENUM('SUCCESS', 'FAILED') NOT NULL DEFAULT 'SUCCESS',
    pos_terminal VARCHAR(50) COMMENT 'Which POS device created the change',
    reference_id VARCHAR(100) COMMENT 'Invoice ID or restock ID',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_sync_status (sync_status),
    INDEX idx_pos_terminal (pos_terminal),
    INDEX idx_date_created (date_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Create comments for clarity
-- ========================================
ALTER TABLE damaged_inventory COMMENT = 'Tracks damaged goods from various sources';
ALTER TABLE pos_stock_sync_logs COMMENT = 'Logs all stock changes synchronized from POS systems';

