-- Add customer_type column to pos_carts
-- Values: POS | ONLINE | WALK_IN

USE beyos_pos;

ALTER TABLE pos_carts
  ADD COLUMN customer_type VARCHAR(20) DEFAULT NULL COMMENT 'POS | ONLINE | WALK_IN' AFTER customer_id;

-- Backfill: treat NULL customer_id as WALK_IN; others as ONLINE by default (POS detection is done in app level)
UPDATE pos_carts
SET customer_type = CASE WHEN customer_id IS NULL THEN 'WALK_IN' ELSE 'ONLINE' END
WHERE customer_type IS NULL;

CREATE INDEX idx_customer_type ON pos_carts(customer_type);

