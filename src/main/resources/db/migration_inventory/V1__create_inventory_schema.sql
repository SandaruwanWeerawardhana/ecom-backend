-- ============================================
-- INVENTORY DATABASE SCHEMA - beyos_inventory_db
-- Migration: V1__create_inventory_schema.sql
-- ============================================

-- ========================================
-- Product Stock Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL COMMENT 'References products.id from product module',
    variant_id BIGINT NULL COMMENT 'References product_variants.id from product module',
    stock_quantity INT NOT NULL DEFAULT 0,
    allow_backorder BOOLEAN NOT NULL DEFAULT FALSE,
    low_stock_threshold INT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_stock_quantity (stock_quantity),
    UNIQUE KEY unique_product_variant_stock (product_id, variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Backorders Table
-- ========================================
CREATE TABLE IF NOT EXISTS backorders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    order_id BIGINT NOT NULL COMMENT 'References orders table from order module',
    quantity INT NOT NULL,
    fulfilled BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_order_id (order_id),
    INDEX idx_fulfilled (fulfilled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Stock Movement Logs Table
-- ========================================
CREATE TABLE IF NOT EXISTS stock_movement_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    movement_type ENUM('IN', 'OUT', 'ADJUSTMENT', 'RETURN') NOT NULL,
    quantity_changed INT NOT NULL,
    quantity_before INT NOT NULL,
    quantity_after INT NOT NULL,
    reference_type VARCHAR(100) COMMENT 'ORDER, PURCHASE, ADJUSTMENT, etc.',
    reference_id BIGINT COMMENT 'ID of the reference entity',
    notes TEXT,
    performed_by VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_movement_type (movement_type),
    INDEX idx_reference_type (reference_type),
    INDEX idx_date_created (date_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Create indexes and comments for clarity
-- ========================================
ALTER TABLE product_stock COMMENT = 'Stores current stock levels for products and variants';
ALTER TABLE backorders COMMENT = 'Tracks backordered items when stock is unavailable';
ALTER TABLE stock_movement_logs COMMENT = 'Audit trail for all stock movements';

