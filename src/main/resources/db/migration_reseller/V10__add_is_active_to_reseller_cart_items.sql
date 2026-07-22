-- V10__add_is_active_to_reseller_cart_items.sql
-- Add is_active column to reseller_cart_items for soft delete support

ALTER TABLE reseller_cart_items
    ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Soft delete flag: 1 = active, 0 = deleted';

-- Index for filtering active items efficiently
CREATE INDEX idx_reseller_cart_items_is_active ON reseller_cart_items (cart_id, is_active);

-- Backfill existing rows as active
UPDATE reseller_cart_items SET is_active = 1 WHERE is_active IS NULL;

