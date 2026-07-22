-- Migration: Add payment_method_id column to orders table
-- Description: Adds payment_method_id as a reference field (NOT a foreign key) to track payment method
-- Author: System
-- Date: 2026-01-21

USE beyos_order;

-- Add payment_method_id column to orders table
ALTER TABLE orders
ADD COLUMN payment_method_id BIGINT NULL
COMMENT 'Reference to payment method (NOT FK - cross-module reference)'
AFTER payment_method;

-- Add index for payment_method_id for better query performance
CREATE INDEX idx_payment_method_id ON orders(payment_method_id);

-- Add comment to table
ALTER TABLE orders
COMMENT = 'Orders table - stores customer/reseller orders with cross-module references';

