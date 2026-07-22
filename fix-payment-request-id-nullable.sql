-- ===============================================================
-- Direct Fix: Make payment_request_id nullable
-- Execute this immediately to fix the constraint violation
-- ===============================================================

USE beyos_payment;

-- Drop the foreign key constraint first
ALTER TABLE payment_transactions DROP FOREIGN KEY payment_transactions_ibfk_1;

-- Now modify the column to be nullable
ALTER TABLE payment_transactions MODIFY COLUMN payment_request_id BIGINT NULL COMMENT 'Payment request ID - nullable for pending payments';

-- Re-add the foreign key constraint with ON DELETE SET NULL
ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_ibfk_1
FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id) ON DELETE SET NULL;

-- Verify the change
SHOW CREATE TABLE payment_transactions;

