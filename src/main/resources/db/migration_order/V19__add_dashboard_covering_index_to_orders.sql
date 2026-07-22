-- Covering index for the dashboard aggregates over active orders:
-- WHERE status IN (...) with conditional SUM(subtotal) over created_at ranges.
-- Lets MySQL satisfy the query from the index alone (no row lookups).
CREATE INDEX idx_orders_status_created_subtotal ON orders (status, created_at, subtotal);
