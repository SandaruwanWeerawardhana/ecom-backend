-- =====================================================
-- Table: shipments
-- Purpose: Track each order shipment with courier
-- =====================================================

USE beyos_delivery;

CREATE TABLE shipments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL COMMENT 'FK to beyos_order.orders.id',
    courier_id BIGINT NOT NULL,
    courier_rate_id BIGINT NULL COMMENT 'FK to courier_rates.id (snapshot of rate used)',
    shipment_weight DECIMAL(10,3) NOT NULL COMMENT 'Total weight in kg',
    shipping_cost DECIMAL(10,2) NOT NULL COMMENT 'Final computed cost',
    shipping_breakdown JSON NULL COMMENT 'Cost breakdown: first_kg, additional, fees, total',
    payment_method_id BIGINT NULL COMMENT 'Payment method chosen for this order',
    payer_type ENUM('CUSTOMER','RESELLER') DEFAULT 'CUSTOMER',
    tracking_number VARCHAR(255) NULL,
    tracking_url VARCHAR(1024) NULL,
    status ENUM('PENDING','BOOKED','IN_TRANSIT','DELIVERED','FAILED','RETURNED') DEFAULT 'PENDING',
    booked_at DATETIME NULL,
    shipped_at DATETIME NULL,
    delivered_at DATETIME NULL,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (courier_id) REFERENCES couriers(id),
    FOREIGN KEY (courier_rate_id) REFERENCES courier_rates(id) ON DELETE SET NULL,
    INDEX idx_order_id (order_id),
    INDEX idx_courier_id (courier_id),
    INDEX idx_tracking_number (tracking_number),
    INDEX idx_status (status),
    INDEX idx_date_created (date_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

