-- V8__create_reseller_price_overrides_table.sql
-- Create reseller_price_overrides table for price override audit trail

CREATE TABLE reseller_price_overrides (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    reseller_id BIGINT NOT NULL,
    order_id BIGINT COMMENT 'References beyos_order.orders (NO FK - cross-database)',
    order_item_id BIGINT COMMENT 'References beyos_order.order_items (NO FK - cross-database)',
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    base_unit_price DECIMAL(10,2) NOT NULL COMMENT 'Original reseller price',
    override_unit_price DECIMAL(10,2) NOT NULL COMMENT 'What reseller set',
    quantity INT NOT NULL,
    margin_amount DECIMAL(10,2) NOT NULL COMMENT 'Calculated profit',
    margin_percentage DECIMAL(5,2) NOT NULL,
    date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reseller_price_overrides_uuid (uuid),
    INDEX idx_reseller_price_overrides_reseller_id (reseller_id),
    INDEX idx_reseller_price_overrides_order_id (order_id),
    INDEX idx_reseller_price_overrides_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

