-- =====================================================
-- Table: pos_product_cache
-- Purpose: Denormalized product cache for POS (Elasticsearch fallback)
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_product_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT 'Reference to product module',
    uuid CHAR(36) NOT NULL COMMENT 'Product UUID',
    title VARCHAR(255) NOT NULL COMMENT 'Product title',
    sku VARCHAR(100) NULL COMMENT 'Stock Keeping Unit',
    price DECIMAL(10,2) NOT NULL COMMENT 'Regular price',
    sale_price DECIMAL(10,2) NULL COMMENT 'Sale price if on sale',
    stock_available INT NOT NULL DEFAULT 0 COMMENT 'Available stock quantity',
    thumbnail_url VARCHAR(500) NULL COMMENT 'Product thumbnail image URL',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Product active status',
    has_variants BOOLEAN DEFAULT FALSE COMMENT 'Whether product has variants',
    synced_at DATETIME NOT NULL COMMENT 'Last sync time from product module',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_uuid (uuid),
    INDEX idx_sku (sku),
    INDEX idx_is_active (is_active),
    FULLTEXT INDEX ft_title_sku (title, sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Denormalized product cache for POS operations';

