-- Supports the admin order list's completed-cart lookup:
-- WHERE is_draft = false AND is_active = false ORDER BY created_at DESC
CREATE INDEX idx_pos_carts_completed_created_at ON pos_carts (is_draft, is_active, created_at);
