-- =====================================================
-- Table: courier_rates
-- Purpose: Pricing per courier (first kg + additional kg)
-- Supports: Customer type, payment method variations
-- =====================================================

USE beyos_delivery;

CREATE TABLE courier_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    courier_id BIGINT NOT NULL,
    customer_type ENUM('CUSTOMER','RESELLER','BOTH') DEFAULT 'BOTH',
    payment_method_id BIGINT NULL COMMENT 'FK to beyos_payment.payment_methods.id (NULL = applies to all)',
    first_kg_price DECIMAL(10,2) NOT NULL COMMENT 'Price for first 1kg',
    additional_kg_price DECIMAL(10,2) NOT NULL COMMENT 'Price per additional kg',
    weight_granularity ENUM('PER_KG','PER_0_5KG','PER_0_1KG') DEFAULT 'PER_KG',
    min_charge DECIMAL(10,2) DEFAULT 0.00,
    max_charge DECIMAL(10,2) DEFAULT NULL,
    effective_from DATETIME NULL,
    effective_to DATETIME NULL,
    is_active BOOLEAN DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (courier_id) REFERENCES couriers(id) ON DELETE CASCADE,
    INDEX idx_courier_id (courier_id),
    INDEX idx_customer_type (customer_type),
    INDEX idx_payment_method_id (payment_method_id),
    INDEX idx_is_active (is_active),
    INDEX idx_effective_dates (effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

