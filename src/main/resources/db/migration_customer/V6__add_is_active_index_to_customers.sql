-- Supports the dashboard's active-customer count:
-- SELECT COUNT(*) FROM customers WHERE is_active = true
CREATE INDEX idx_customers_is_active ON customers (is_active);
