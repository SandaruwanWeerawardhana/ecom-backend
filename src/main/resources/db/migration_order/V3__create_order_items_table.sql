-- =====================================================
-- Table: order_items
-- Purpose: Line items for each order (snapshot of product/price)
-- =====================================================

USE beyos_order;

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,

    product_title VARCHAR(255) NOT NULL COMMENT 'Snapshot of product name at order time',
    variant_title VARCHAR(255) NULL COMMENT 'Snapshot of variant name',

    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL COMMENT 'Price per unit at order time',
    total_price DECIMAL(10,2) NOT NULL COMMENT 'unit_price * quantity',

    item_weight DECIMAL(10,3) NULL COMMENT 'Weight per item snapshot (kg)',
    item_total_weight DECIMAL(10,3) NULL COMMENT 'item_weight * quantity',

    is_refunded BOOLEAN DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

