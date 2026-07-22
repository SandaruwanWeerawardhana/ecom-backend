-- ============================================
-- WISHLIST ITEMS TABLE
-- Migration: V3__create_wishlist_items_table.sql
-- Database: beyos_customers_db
-- ============================================

-- Wishlist Items Table
CREATE TABLE IF NOT EXISTS wishlist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL COMMENT 'Reference to product in beyos_product_db',
    variant_id BIGINT COMMENT 'Optional: specific variant reference',
    priority VARCHAR(20),
    notes TEXT,
    tags JSON COMMENT 'JSON array for custom tags',
    price_snapshot DECIMAL(25,6) COMMENT 'Price when added to wishlist',
    sale_price_snapshot DECIMAL(25,6) COMMENT 'Sale price when added',
    captured_at DATETIME NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(50) COMMENT 'Source: WEB, MOBILE_APP, etc.',
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    UNIQUE KEY uk_customer_product (customer_id, product_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_product_id (product_id),
    INDEX idx_is_available (is_available)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

