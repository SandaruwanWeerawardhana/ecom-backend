-- V11__enhance_shipment_tracking_with_enum_status.sql
-- Migration to enhance shipment tracking:
-- 1. Add event_source column to track event origin
-- 2. Create index on event_source for better query performance
-- 3. Add NOT NULL constraint to shipment_tracking_events.status

-- Add event_source column to shipment_tracking_events if it doesn't exist
ALTER TABLE shipment_tracking_events
ADD COLUMN event_source VARCHAR(50) NULL COMMENT 'Source of the event: COURIER, SYSTEM, MANUAL, PICKUP_REQUEST, etc.';

-- Create index on event_source for faster lookups
CREATE INDEX idx_shipment_tracking_event_source ON shipment_tracking_events(event_source);

-- Add NOT NULL constraint to status column in shipment_tracking_events
ALTER TABLE shipment_tracking_events
MODIFY COLUMN status VARCHAR(100) NOT NULL COMMENT 'ShipmentStatus enum: PENDING, BOOKED, REQUESTED_PICK_UP, PROCESSING, PICKED, ON_QC, IN_TRANSIT, COLLECTED_BY_KOOMBIYO, DISPATCH_TO_DESTINATION, RECEIVED_AT_DESTINATION, OUT_FOR_DELIVERY, DELIVERED, DELIVERED_NOT_CONFIRMED, PARTIALLY_DELIVERED, PARTIALLY_DELIVERED_NOT_CONFIRMED, CLIENT_RECEIVED, FAILED_TO_DELIVER, RESCHEDULED, RETURN_TO_CLIENT, RETURN_TO_HO, EXCHANGE_COLLECTED, EXCHANGE_RECEIVED, DIFFERENT_DESTINATION, PENDING_DIFFERENT_DESTINATION, RECEIVED_AT_HO, RECEIVED_AT_WAREHOUSE, HOLD, PURCHASE_BY_KOOMBIYO, CONFIRMED_BY_BRANCH, FAILED, RETURNED';

