-- V11__set_status_reseller_wallet_transactions.sql
-- Add status column to reseller_wallet_transactions with default PENDING

ALTER TABLE reseller_wallet_transactions
ADD COLUMN `status` ENUM('SUCCESS', 'FAIL', 'PENDING', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'Outcome of the transaction';

ALTER TABLE reseller_wallet_transactions
ADD INDEX idx_reseller_wallet_transactions_status (status);
