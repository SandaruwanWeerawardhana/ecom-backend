-- Supports the POS order list's batched per-page lookup and the
-- existing-order idempotency checks:
-- WHERE cart_id IN (...) AND source = 'POS'
CREATE INDEX idx_cart_id ON orders (cart_id);
