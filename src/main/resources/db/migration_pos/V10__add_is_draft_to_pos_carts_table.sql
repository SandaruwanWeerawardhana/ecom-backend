-- =====================================================
-- Migration: V10__add_is_draft_to_pos_carts_table.sql
-- Purpose: Add `is_draft` boolean column to pos_carts (default FALSE) and index
-- =====================================================

USE beyos_pos;

-- Add a non-null boolean column to indicate carts that are drafts (not active transactions)
ALTER TABLE pos_carts
  ADD COLUMN is_draft BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Draft cart flag (true = draft, false = normal)' AFTER is_active;

-- Add an index to support queries filtering by draft status
ALTER TABLE pos_carts
  ADD INDEX idx_is_draft (is_draft);

-- End of migration

