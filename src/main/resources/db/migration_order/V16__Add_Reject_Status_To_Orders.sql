-- ============================================
-- Add REJECT Status to Orders
-- Migration: V16__Add_Reject_Status_To_Orders.sql
-- ============================================

-- Add REJECTED status to orders table ENUM
-- This status indicates an order has been rejected by admin
-- Status flow: Any status → REJECTED (when rejected by admin)

ALTER TABLE orders
    MODIFY COLUMN status ENUM(
    'PENDING',
    'PAID',
    'PROCESSING',
    'COURIER_ORDER_PLACED',
    'OUT_FOR_DELIVERY',
    'DELIVERED',
    'COMPLETED',
    'CANCELLED',
    'REJECTED',
    'RETURN_REQUESTED',
    'RETURN_APPROVED',
    'REFUND_INITIATED',
    'REFUNDED'
    ) DEFAULT 'PENDING';

-- Migration adds REJECTED as a new order status
-- This allows admins to reject orders that cannot be fulfilled
-- Date: 2026-05-19

