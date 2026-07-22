-- ============================================
-- INVENTORY DATABASE SCHEMA - beyos_inventory_db
-- Migration: V2__create_low_stock_alerts_table.sql
-- ============================================

-- ========================================
-- Low Stock Alerts Table
-- ========================================
CREATE TABLE IF NOT EXISTS low_stock_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL COMMENT 'References products.id from product module',
    variant_id BIGINT NULL COMMENT 'References product_variants.id from product module',
    current_stock INT NOT NULL,
    threshold_level INT NOT NULL,
    alert_status ENUM('PENDING', 'ACKNOWLEDGED', 'RESOLVED') NOT NULL DEFAULT 'PENDING',
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    notified_at DATETIME NULL,
    acknowledged_by VARCHAR(100) NULL,
    acknowledged_at DATETIME NULL,
    resolved_at DATETIME NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_alert_status (alert_status),
    INDEX idx_notified (notified),
    INDEX idx_date_created (date_created),
    UNIQUE KEY unique_product_variant_alert (product_id, variant_id, alert_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE low_stock_alerts COMMENT = 'Tracks low stock alerts for products and variants';

