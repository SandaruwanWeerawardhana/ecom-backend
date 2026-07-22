-- =====================================================
-- Table: courier_payment_method_map
-- Purpose: Map couriers to payment methods with free shipping flag
-- =====================================================

USE beyos_delivery;

CREATE TABLE courier_payment_method_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    courier_id BIGINT NOT NULL,
    payment_method_id BIGINT NOT NULL,
    is_fee_free BOOLEAN DEFAULT FALSE COMMENT 'If true, courier charge is waived for this payment method',
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (courier_id) REFERENCES couriers(id) ON DELETE CASCADE,
    UNIQUE KEY uk_courier_payment (courier_id, payment_method_id),
    INDEX idx_courier_id (courier_id),
    INDEX idx_payment_method_id (payment_method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

