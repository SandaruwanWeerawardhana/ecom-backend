-- =====================================================
-- Table: pos_cart_items
-- Purpose: Individual products in POS carts
-- =====================================================

USE beyos_pos;

CREATE TABLE pos_cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public cart item UUID',
    cart_id BIGINT NOT NULL COMMENT 'FK to pos_carts',
    product_id BIGINT NOT NULL COMMENT 'Reference to product (no FK - microservices)',
    variant_id BIGINT NULL COMMENT 'Reference to variant (NULL for simple products)',
    quantity INT NOT NULL DEFAULT 1 COMMENT 'Item quantity',
    unit_price DECIMAL(10,2) NOT NULL COMMENT 'Price snapshot at time of adding',
    total_price DECIMAL(10,2) NOT NULL COMMENT 'unit_price × quantity',
    stock_available INT NOT NULL DEFAULT 0 COMMENT 'Stock snapshot when item added',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_cart_id (cart_id),
    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),

    CONSTRAINT fk_pos_cart_items_cart FOREIGN KEY (cart_id)
        REFERENCES pos_carts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Items in active POS carts';

