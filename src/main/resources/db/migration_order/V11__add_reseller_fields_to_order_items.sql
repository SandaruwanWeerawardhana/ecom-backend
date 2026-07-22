-- V11__add_reseller_fields_to_order_items.sql
-- Add reseller-specific fields to order_items table

ALTER TABLE order_items
ADD COLUMN unit_base_price DECIMAL(10,2) NULL COMMENT 'Reseller purchase price from supplier',
ADD COLUMN reseller_margin_amount DECIMAL(10,2) NULL COMMENT 'Calculated profit per item for reseller orders';

-- Add index for reseller order queries (assuming orders table has reseller_id)
-- This improves performance when querying reseller orders
CREATE INDEX idx_order_items_reseller_queries ON order_items(order_id);

