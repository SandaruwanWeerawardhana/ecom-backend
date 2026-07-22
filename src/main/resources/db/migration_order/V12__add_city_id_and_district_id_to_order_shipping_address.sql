-- =====================================================
-- Migration: V12__add_city_id_and_district_id_to_order_shipping_address.sql
-- Purpose: Add optional cityId and districtId fields to order_shipping_address
--          for courier handling integration
-- =====================================================

USE beyos_order;

ALTER TABLE order_shipping_address
    ADD COLUMN city_id BIGINT NULL COMMENT 'Optional courier city reference ID' AFTER city,
    ADD COLUMN district_id BIGINT NULL COMMENT 'Optional courier district reference ID' AFTER city_id;

CREATE INDEX idx_city_id ON order_shipping_address (city_id);
CREATE INDEX idx_district_id ON order_shipping_address (district_id);

