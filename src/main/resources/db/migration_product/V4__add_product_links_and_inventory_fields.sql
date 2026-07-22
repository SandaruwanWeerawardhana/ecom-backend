-- ============================================
-- PRODUCT DATABASE SCHEMA UPDATES
-- Migration: V4__add_product_links_and_inventory_fields.sql
-- Description: Add initial stock quantity for simple products and inventory status
-- ============================================

-- ========================================
-- Add initial_stock_quantity to products table (for SIMPLE products)
-- ========================================
ALTER TABLE products
ADD COLUMN initial_stock_quantity INT DEFAULT 0 COMMENT 'Initial stock quantity for simple products';

-- ========================================
-- Add inventory_status to products table
-- This tracks the stock status at product level
-- ========================================
ALTER TABLE products
ADD COLUMN inventory_status ENUM('IN_STOCK', 'OUT_OF_STOCK', 'ON_BACKORDER') NOT NULL DEFAULT 'IN_STOCK' COMMENT 'Product inventory status';

-- Add index for inventory_status for faster queries
CREATE INDEX idx_inventory_status ON products(inventory_status);

-- ========================================
-- Add inventory_status to product_variants table
-- This tracks the stock status at variant level
-- ========================================
ALTER TABLE product_variants
ADD COLUMN inventory_status ENUM('IN_STOCK', 'OUT_OF_STOCK', 'ON_BACKORDER') NOT NULL DEFAULT 'IN_STOCK' COMMENT 'Variant inventory status';

-- Add index for variant inventory_status
CREATE INDEX idx_variant_inventory_status ON product_variants(inventory_status);

