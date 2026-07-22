-- =====================================================
-- Migration: V11__add_foreign_keys_to_payment_tables.sql
-- Purpose: Add missing foreign key constraints and indexes to payment module tables
-- Date: 2026-01-09
-- =====================================================

USE beyos_payment;

-- =====================================================
-- 1. Add method_id to payment_refunds for better querying
--    (Currently payment_refunds only references transaction_id)
-- =====================================================

-- Add method_id column to payment_refunds table
ALTER TABLE payment_refunds
ADD COLUMN method_id BIGINT NULL COMMENT 'Payment method used for the original transaction'
AFTER transaction_id;

-- Populate method_id from related payment_transactions
UPDATE payment_refunds pr
INNER JOIN payment_transactions pt ON pr.transaction_id = pt.id
SET pr.method_id = pt.method_id;

-- Add foreign key constraint to payment_methods
ALTER TABLE payment_refunds
ADD CONSTRAINT fk_payment_refunds_method_id
FOREIGN KEY (method_id) REFERENCES payment_methods(id) ON DELETE SET NULL;

-- Add index on method_id for better query performance
CREATE INDEX idx_method_id ON payment_refunds(method_id);

-- =====================================================
-- 2. Verify all existing foreign key constraints
--    (These should already exist, but ensuring consistency)
-- =====================================================

-- Verify payment_logs has FK to payment_methods (already exists from V7)
-- Verify payment_requests has FK to payment_methods (already exists from V4)
-- Verify payment_transactions has FK to payment_methods (already exists from V5)
-- Verify pos_payments has FK to payment_methods (already exists from V8)
-- Verify payment_method_config has FK to payment_methods (already exists from V3)
-- Verify payment_method_fees has FK to payment_methods (already exists from V10)

-- =====================================================
-- 3. Add composite indexes for better query performance
-- =====================================================

-- Composite index for filtering refunds by method and status
CREATE INDEX idx_refunds_method_status ON payment_refunds(method_id, status);

-- Composite index for filtering transactions by method and status
CREATE INDEX idx_transactions_method_status ON payment_transactions(method_id, status);

-- Composite index for filtering requests by method and status
CREATE INDEX idx_requests_method_status ON payment_requests(method_id, status);

-- =====================================================
-- 4. Verify data integrity
-- =====================================================

-- Check for any orphaned records (should return 0 rows)
-- Uncomment to run manually if needed:
/*
SELECT 'payment_refunds orphaned records' as check_name, COUNT(*) as count
FROM payment_refunds pr
LEFT JOIN payment_methods pm ON pr.method_id = pm.id
WHERE pr.method_id IS NOT NULL AND pm.id IS NULL

UNION ALL

SELECT 'payment_logs orphaned records', COUNT(*)
FROM payment_logs pl
LEFT JOIN payment_methods pm ON pl.method_id = pm.id
WHERE pl.method_id IS NOT NULL AND pm.id IS NULL

UNION ALL

SELECT 'payment_requests orphaned records', COUNT(*)
FROM payment_requests pr
LEFT JOIN payment_methods pm ON pr.method_id = pm.id
WHERE pm.id IS NULL

UNION ALL

SELECT 'payment_transactions orphaned records', COUNT(*)
FROM payment_transactions pt
LEFT JOIN payment_methods pm ON pt.method_id = pm.id
WHERE pm.id IS NULL

UNION ALL

SELECT 'pos_payments orphaned records', COUNT(*)
FROM pos_payments pp
LEFT JOIN payment_methods pm ON pp.method_id = pm.id
WHERE pm.id IS NULL;
*/

