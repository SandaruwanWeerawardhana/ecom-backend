-- Supports the POS order list lookup:
-- WHERE source = 'POS' AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CREATE INDEX idx_orders_source_created_at ON orders (source, created_at);
