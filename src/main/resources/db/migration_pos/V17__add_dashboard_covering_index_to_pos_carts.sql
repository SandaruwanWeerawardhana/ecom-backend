-- Covering index for the dashboard aggregates over completed carts:
-- WHERE is_draft = false AND is_active = false with conditional SUM(subtotal)
-- over created_at ranges. Lets MySQL satisfy the query from the index alone.
CREATE INDEX idx_pos_carts_completed_covering ON pos_carts (is_draft, is_active, created_at, subtotal);

-- idx_pos_carts_completed_created_at (from V16) is redundant now because the new
-- index shares its leading columns. Drop it only if it exists, since some
-- environments have drifted and no longer have it.
SET @old_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'pos_carts'
      AND index_name = 'idx_pos_carts_completed_created_at'
);
SET @drop_stmt := IF(@old_index_exists > 0,
    'DROP INDEX idx_pos_carts_completed_created_at ON pos_carts',
    'SELECT 1');
PREPARE stmt FROM @drop_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
