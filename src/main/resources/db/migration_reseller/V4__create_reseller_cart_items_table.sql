-- V4__create_reseller_cart_items_table.sql
-- Create reseller_cart_items table for reseller cart items

CREATE TABLE reseller_cart_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL COMMENT 'References beyos_product_db.products (NO FK - cross-database)',
    variant_id BIGINT COMMENT 'References beyos_product_db.variants (NO FK - cross-database, nullable for simple products)',
    product_name VARCHAR(255),
    variant_name VARCHAR(255),
    base_unit_price DECIMAL(10,2) NOT NULL COMMENT 'Reseller purchase price from supplier',
    override_unit_price DECIMAL(10,2) COMMENT 'Reseller selling price (customer pays this)',
    quantity INT NOT NULL DEFAULT 1,
    total_price DECIMAL(10,2) NOT NULL,
    margin_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Calculated profit per item',
    date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reseller_cart_items_uuid (uuid),
    INDEX idx_reseller_cart_items_cart_id (cart_id),
    INDEX idx_reseller_cart_items_product_id (product_id),
    INDEX idx_reseller_cart_items_variant_id (variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

