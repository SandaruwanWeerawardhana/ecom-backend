-- =====================================================
-- Migration: Insert Sample Couriers and Courier Rates
-- Purpose: Add sample data for testing shipping cost calculation
-- =====================================================

USE beyos_delivery;

-- Insert sample couriers
INSERT INTO couriers (uuid, code, name, description, contact_phone, email, is_active)
VALUES
    ('310fd1f5-bb9a-428a-a43a-f2496898a355', 'DHL_EXPRESS', 'DHL Express', 'International and domestic express delivery', '+94-11-2345678', 'info@dhl.lk', TRUE),
    ('420fe2e6-cc0b-539b-b54b-g3507909b466', 'FEDEX', 'FedEx', 'Global courier and logistics service', '+94-11-3456789', 'contact@fedex.lk', TRUE),
    ('530gf3f7-dd1c-640c-c65c-h4618010c577', 'UPS', 'UPS', 'United Parcel Service', '+94-11-4567890', 'support@ups.lk', TRUE),
    ('640hg4g8-ee2d-751d-d76d-i5729121d688', 'LOCAL_EXPRESS', 'Local Express', 'Fast local delivery within Sri Lanka', '+94-11-5678901', 'hello@localexpress.lk', TRUE),
    ('750ih5h9-ff3e-862e-e87e-j6830232e799', 'PRONTO', 'Pronto Lanka', 'Same-day delivery service', '+94-11-6789012', 'info@pronto.lk', TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    date_updated = CURRENT_TIMESTAMP;

-- Insert sample courier rates for DHL Express
INSERT INTO courier_rates (uuid, courier_id, customer_type, payment_method_id, first_kg_price, additional_kg_price, weight_granularity, min_charge, max_charge, is_active)
SELECT
    '810ji6i0-gg4f-973f-f98f-k7941343f800',
    c.id,
    'CUSTOMER',
    NULL, -- Applies to all payment methods
    500.00,
    250.00,
    'PER_KG',
    400.00,
    5000.00,
    TRUE
FROM couriers c
WHERE c.uuid = '310fd1f5-bb9a-428a-a43a-f2496898a355'
ON DUPLICATE KEY UPDATE
    first_kg_price = VALUES(first_kg_price),
    additional_kg_price = VALUES(additional_kg_price),
    date_updated = CURRENT_TIMESTAMP;

-- Insert sample courier rates for DHL Express (RESELLER - discounted)
INSERT INTO courier_rates (uuid, courier_id, customer_type, payment_method_id, first_kg_price, additional_kg_price, weight_granularity, min_charge, max_charge, is_active)
SELECT
    '920kj7j1-hh5g-084g-g09g-l8052454g911',
    c.id,
    'RESELLER',
    NULL,
    400.00,
    200.00,
    'PER_KG',
    350.00,
    5000.00,
    TRUE
FROM couriers c
WHERE c.uuid = '310fd1f5-bb9a-428a-a43a-f2496898a355'
ON DUPLICATE KEY UPDATE
    first_kg_price = VALUES(first_kg_price),
    additional_kg_price = VALUES(additional_kg_price),
    date_updated = CURRENT_TIMESTAMP;

-- Insert sample courier rates for FedEx
INSERT INTO courier_rates (uuid, courier_id, customer_type, payment_method_id, first_kg_price, additional_kg_price, weight_granularity, min_charge, max_charge, is_active)
SELECT
    'a30lk8k2-ii6h-195h-h10h-m9163565h022',
    c.id,
    'BOTH',
    NULL,
    550.00,
    275.00,
    'PER_KG',
    450.00,
    6000.00,
    TRUE
FROM couriers c
WHERE c.uuid = '420fe2e6-cc0b-539b-b54b-g3507909b466'
ON DUPLICATE KEY UPDATE
    first_kg_price = VALUES(first_kg_price),
    additional_kg_price = VALUES(additional_kg_price),
    date_updated = CURRENT_TIMESTAMP;

-- Insert sample courier rates for UPS
INSERT INTO courier_rates (uuid, courier_id, customer_type, payment_method_id, first_kg_price, additional_kg_price, weight_granularity, min_charge, max_charge, is_active)
SELECT
    'b40ml9l3-jj7i-206i-i21i-n0274676i133',
    c.id,
    'BOTH',
    NULL,
    600.00,
    300.00,
    'PER_0_5KG',
    500.00,
    7000.00,
    TRUE
FROM couriers c
WHERE c.uuid = '530gf3f7-dd1c-640c-c65c-h4618010c577'
ON DUPLICATE KEY UPDATE
    first_kg_price = VALUES(first_kg_price),
    additional_kg_price = VALUES(additional_kg_price),
    date_updated = CURRENT_TIMESTAMP;

-- Insert sample courier rates for Local Express (Affordable local delivery)
INSERT INTO courier_rates (uuid, courier_id, customer_type, payment_method_id, first_kg_price, additional_kg_price, weight_granularity, min_charge, max_charge, is_active)
SELECT
    'c50nm0m4-kk8j-317j-j32j-o1385787j244',
    c.id,
    'BOTH',
    NULL,
    150.00,
    75.00,
    'PER_KG',
    100.00,
    1500.00,
    TRUE
FROM couriers c
WHERE c.uuid = '640hg4g8-ee2d-751d-d76d-i5729121d688'
ON DUPLICATE KEY UPDATE
    first_kg_price = VALUES(first_kg_price),
    additional_kg_price = VALUES(additional_kg_price),
    date_updated = CURRENT_TIMESTAMP;

-- Insert sample courier rates for Pronto (Same-day premium)
INSERT INTO courier_rates (uuid, courier_id, customer_type, payment_method_id, first_kg_price, additional_kg_price, weight_granularity, min_charge, max_charge, is_active)
SELECT
    'd60on1n5-ll9k-428k-k43k-p2496898k355',
    c.id,
    'BOTH',
    NULL,
    300.00,
    150.00,
    'PER_0_1KG',
    250.00,
    2000.00,
    TRUE
FROM couriers c
WHERE c.uuid = '750ih5h9-ff3e-862e-e87e-j6830232e799'
ON DUPLICATE KEY UPDATE
    first_kg_price = VALUES(first_kg_price),
    additional_kg_price = VALUES(additional_kg_price),
    date_updated = CURRENT_TIMESTAMP;

-- Verify data
SELECT
    c.uuid as courier_uuid,
    c.name as courier_name,
    cr.uuid as rate_uuid,
    cr.customer_type,
    cr.first_kg_price,
    cr.additional_kg_price,
    cr.weight_granularity,
    cr.min_charge,
    cr.max_charge,
    cr.is_active
FROM couriers c
LEFT JOIN courier_rates cr ON c.id = cr.courier_id
WHERE c.is_active = TRUE
ORDER BY c.name, cr.customer_type;

