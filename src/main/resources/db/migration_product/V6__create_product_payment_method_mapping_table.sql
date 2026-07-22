-- V6__create_product_payment_method_mapping_table.sql
-- Product Payment Method Mapping Table
-- Maps which payment methods are allowed for each product
-- NO cross-database foreign keys — payment_method_id references beyos_payment.payment_methods logically only

CREATE TABLE product_payment_method_mappings (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid        VARCHAR(36) NOT NULL UNIQUE,
    product_id  BIGINT NOT NULL COMMENT 'References beyos_product_db.products (NO FK - same DB)',
    payment_method_id   BIGINT NOT NULL COMMENT 'References beyos_payment.payment_methods (NO FK - cross-database)',
    payment_method_code VARCHAR(50) NOT NULL COMMENT 'Snapshot of payment method code (e.g. COD, BANK_CARD)',
    is_active   TINYINT(1) NOT NULL DEFAULT 1,
    date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_ppmm_product_id (product_id),
    INDEX idx_ppmm_payment_method_id (payment_method_id),
    INDEX idx_ppmm_uuid (uuid),
    INDEX idx_ppmm_product_active (product_id, is_active),
    CONSTRAINT uq_ppmm_product_payment UNIQUE (product_id, payment_method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Maps allowed payment methods per product. No cross-DB FK.';

