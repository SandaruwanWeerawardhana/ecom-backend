-- ============================================
-- Add COURIER_ORDER_PLACED Status to Orders
-- Migration: V15__Add_Courier_Order_Placed_Status.sql
-- ============================================

-- Add COURIER_ORDER_PLACED status to orders table ENUM
-- This status indicates an order has been successfully placed with a courier service
-- Status flow: PENDING → PAID → COURIER_ORDER_PLACED → OUT_FOR_DELIVERY → DELIVERED → COMPLETED

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
    'RETURN_REQUESTED',
    'RETURN_APPROVED',
    'REFUND_INITIATED',
    'REFUNDED'
    ) DEFAULT 'PENDING';

-- Migration adds COURIER_ORDER_PLACED as a new order status
-- This allows better tracking of orders that have been placed with courier service
-- Date: 2026-03-26
