-- Migration: V9__attribute_value_unique_include_is_active.sql
-- Change unique constraint so uniqueness is enforced per (attribute_id, value, is_active)
-- This allows inserting a new ACTIVE row for a value when an existing row for the same value is deactivated.

-- Drop the existing unique index (V1 created it) and add a new one including is_active.
-- Use ALTER TABLE DROP INDEX (no IF EXISTS) because not all MySQL versions support IF EXISTS for DROP INDEX.
ALTER TABLE product_attribute_values
  DROP INDEX unique_attribute_value;

ALTER TABLE product_attribute_values
  ADD UNIQUE KEY unique_attribute_value (attribute_id, value, is_active);

