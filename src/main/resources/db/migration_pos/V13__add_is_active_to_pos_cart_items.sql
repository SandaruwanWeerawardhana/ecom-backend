-- =====================================================
-- Migration: V5 - add is_active to pos_cart_items
-- Purpose: Add an `is_active` boolean column with default TRUE and an index
-- =====================================================

USE beyos_pos;

-- Add the column with NOT NULL + DEFAULT TRUE. For MySQL, BOOLEAN is an alias for TINYINT(1).
ALTER TABLE pos_cart_items
    ADD COLUMN is_active BOOLEAN DEFAULT TRUE COMMENT 'Whether the cart item is active' AFTER stock_available;

-- Add an index to speed up queries that filter by active flag
ALTER TABLE pos_cart_items
    ADD INDEX idx_is_active (is_active);


