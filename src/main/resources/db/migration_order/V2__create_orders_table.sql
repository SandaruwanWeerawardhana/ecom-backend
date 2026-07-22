-- =====================================================
-- Table: orders
-- Purpose: Main order table with shipping and payment info
-- =====================================================

USE beyos_order;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public order ID',
    customer_id BIGINT NULL COMMENT 'FK to customer (NULL for guest checkout)',
    reseller_id BIGINT NULL COMMENT 'FK to reseller (NULL if not reseller order)',
    cart_id BIGINT NULL COMMENT 'FK to cart for tracking',
    order_number VARCHAR(50) UNIQUE NOT NULL COMMENT 'Human-friendly ID: BYS-20250109-9821',

    status ENUM(
        'PENDING',
        'PAID',
        'PROCESSING',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'COMPLETED',
        'CANCELLED',
        'RETURN_REQUESTED',
        'RETURN_APPROVED',
        'REFUND_INITIATED',
        'REFUNDED'
    ) DEFAULT 'PENDING',

    payment_status ENUM('UNPAID','PAID','FAILED','REFUNDED') DEFAULT 'UNPAID',
    payment_method VARCHAR(50) NULL COMMENT 'Payment method code: cod, card, pos_card',
    payment_reference VARCHAR(100) NULL COMMENT 'Gateway transaction reference',

    subtotal DECIMAL(10,2) NOT NULL COMMENT 'Items total only',
    discount_total DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Sum of all discounts',
    shipping_cost DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Courier charge',
    shipping_weight DECIMAL(10,3) DEFAULT 0.000 COMMENT 'Total weight in kg',
    shipping_breakdown JSON NULL COMMENT 'Shipping cost breakdown JSON',
    shipping_payer ENUM('CUSTOMER','RESELLER') DEFAULT 'CUSTOMER',
    shipment_id BIGINT NULL COMMENT 'FK to beyos_delivery.shipments.id',
    total DECIMAL(10,2) NOT NULL COMMENT 'Final total = subtotal - discount + shipping',

    promo_code VARCHAR(100) NULL,
    promo_discount DECIMAL(10,2) NULL,

    notes VARCHAR(500) NULL COMMENT 'Customer delivery notes',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_customer_id (customer_id),
    INDEX idx_reseller_id (reseller_id),
    INDEX idx_order_number (order_number),
    INDEX idx_status (status),
    INDEX idx_payment_status (payment_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
