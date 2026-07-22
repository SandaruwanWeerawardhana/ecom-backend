-- =====================================================
-- Alter orders table to support POS orders
-- Purpose: Add POS-specific fields to unified orders table
-- =====================================================

USE beyos_order;

-- Add POS support columns to existing orders table
ALTER TABLE orders
    ADD COLUMN source ENUM('ONLINE','POS') DEFAULT 'ONLINE' COMMENT 'Order source: online or POS' AFTER order_number,
    ADD COLUMN pos_terminal_id BIGINT NULL COMMENT 'FK to pos_terminals (NULL for online orders)' AFTER source,
    ADD COLUMN pos_cashier_id BIGINT NULL COMMENT 'FK to pos_cashiers (NULL for online orders)' AFTER pos_terminal_id,
    ADD COLUMN card_last_four_digits VARCHAR(4) NULL COMMENT 'Last 4 digits of card (for card payments)' AFTER payment_reference;

-- Add indexes for POS fields
ALTER TABLE orders
    ADD INDEX idx_source (source),
    ADD INDEX idx_pos_terminal_id (pos_terminal_id),
    ADD INDEX idx_pos_cashier_id (pos_cashier_id);

