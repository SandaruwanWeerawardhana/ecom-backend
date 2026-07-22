-- =====================================================
-- Alter order_items table to support POS orders
-- Purpose: Add source tracking to order items
-- =====================================================

USE beyos_order;

-- Add POS support column to order_items
ALTER TABLE order_items
    ADD COLUMN source ENUM('ONLINE','POS') DEFAULT 'ONLINE' COMMENT 'Order item source' AFTER id;

-- Add index for source field
ALTER TABLE order_items
    ADD INDEX idx_source (source);

