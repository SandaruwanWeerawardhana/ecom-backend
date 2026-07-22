-- ===============================================================
-- Migration: Update shipment_tracking_events status to ENUM
-- Database: beyos_delivery
-- Purpose: Convert status from VARCHAR to ENUM for type safety
-- ===============================================================

USE beyos_delivery;

-- Modify the status column from VARCHAR to ENUM with all supported statuses
ALTER TABLE shipment_tracking_events
    MODIFY COLUMN status ENUM(
    'PENDING',
    'BOOKED',
    'REQUESTED_PICK_UP',
    'PROCESSING',
    'PICKED',
    'ON_QC',
    'IN_TRANSIT',
    'COLLECTED_BY_KOOMBIYO',
    'DISPATCH_TO_DESTINATION',
    'RECEIVED_AT_DESTINATION',
    'OUT_FOR_DELIVERY',
    'DELIVERED',
    'DELIVERED_NOT_CONFIRMED',
    'PARTIALLY_DELIVERED',
    'PARTIALLY_DELIVERED_NOT_CONFIRMED',
    'CLIENT_RECEIVED',
    'FAILED_TO_DELIVER',
    'RESCHEDULED',
    'RETURN_TO_CLIENT',
    'RETURN_TO_HO',
    'EXCHANGE_COLLECTED',
    'EXCHANGE_RECEIVED',
    'DIFFERENT_DESTINATION',
    'PENDING_DIFFERENT_DESTINATION',
    'RECEIVED_AT_HO',
    'RECEIVED_AT_WAREHOUSE',
    'HOLD',
    'PURCHASE_BY_KOOMBIYO',
    'CONFIRMED_BY_BRANCH',
    'FAILED',
    'RETURNED'
    ) NOT NULL
    COMMENT 'ShipmentStatus enum: tracks event status changes';
