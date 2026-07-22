-- ===============================================================
-- Migration: Make method_id nullable in payment_transactions
-- Database: beyos_payment
-- Purpose: Allow payment transactions without payment method for COD/pending payments
-- ===============================================================

USE beyos_payment;

-- Drop the existing foreign key constraint on method_id
ALTER TABLE payment_transactions DROP FOREIGN KEY payment_transactions_ibfk_2;

-- Modify method_id column to be nullable
ALTER TABLE payment_transactions MODIFY COLUMN method_id BIGINT NULL COMMENT 'Payment method ID - nullable for COD/pending payments';

-- Re-add the foreign key constraint with ON DELETE SET NULL
ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_ibfk_2
FOREIGN KEY (method_id) REFERENCES payment_methods(id) ON DELETE SET NULL;

-- Verify the changes
SHOW CREATE TABLE payment_transactions;

