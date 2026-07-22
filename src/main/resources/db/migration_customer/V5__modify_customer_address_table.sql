-- ===========================
-- Migration script to modify the customer_address table add province
-- Migration: V5__modify_customer_address_table.sql
-- ===========================

-- Add province column to customer_address table
ALTER TABLE addresses
ADD COLUMN province VARCHAR(255) AFTER city;

--