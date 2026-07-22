-- V7__add_is_reseller_product_to_products.sql
-- Add is_reseller_product column to products table
-- Indicates whether this product is available for resellers to sell

ALTER TABLE products
    ADD COLUMN is_reseller_product TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'Whether this product is available for resellers to sell'
        AFTER is_publish;

CREATE INDEX idx_is_reseller_product ON products (is_reseller_product);

