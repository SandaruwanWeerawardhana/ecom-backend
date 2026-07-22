-- ===============================================================
-- Migration: Add pickup_id column to orders table
-- Database: beyos_order
-- Purpose: Store Koombiyo pickup ID for tracking
-- ===============================================================

USE beyos_order;

-- Add pickup_id column if it doesn't exist
ALTER TABLE orders ADD COLUMN pickup_id BIGINT NULL COMMENT 'Pickup ID from Koombiyo courier';

-- Add index for faster lookups
ALTER TABLE orders ADD INDEX idx_pickup_id (pickup_id);

