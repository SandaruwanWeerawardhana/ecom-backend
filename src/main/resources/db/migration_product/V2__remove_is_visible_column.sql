-- ============================================
-- PRODUCT DATABASE MIGRATION
-- Migration: V2__remove_is_visible_column.sql
-- Description: Remove is_visible column as it's redundant with visibility enum
-- ============================================

ALTER TABLE products DROP COLUMN is_visible;

