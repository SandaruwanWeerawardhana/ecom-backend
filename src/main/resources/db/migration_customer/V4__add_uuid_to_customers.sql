-- ============================================
-- Add UUID column to customers table
-- Migration: V4__add_uuid_to_customers.sql
-- ============================================

-- Add uuid column to customers table
ALTER TABLE customers
ADD COLUMN uuid VARCHAR(36) UNIQUE COMMENT 'Unique identifier for cross-module references';

-- Generate UUIDs for existing customers
UPDATE customers
SET uuid = UUID()
WHERE uuid IS NULL;

-- Make uuid NOT NULL after populating
ALTER TABLE customers
MODIFY COLUMN uuid VARCHAR(36) NOT NULL UNIQUE COMMENT 'Unique identifier for cross-module references';

-- Add index for better performance
CREATE INDEX idx_uuid ON customers(uuid);

