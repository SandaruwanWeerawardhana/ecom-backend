-- =====================================================
-- Table: payment_requests
-- Purpose: Track every payment attempt (Online/POS/Offline)
-- =====================================================

USE beyos_payment;

CREATE TABLE payment_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    order_id BIGINT NULL COMMENT 'Null for POS (order created after payment)',
    method_id BIGINT NOT NULL,
    gateway_transaction_id VARCHAR(100) NULL COMMENT 'Transaction ID from gateway (OnePay, PayHere, etc.)',
    request_payload JSON NULL COMMENT 'Full request JSON',
    response_payload JSON NULL COMMENT 'Full response JSON',
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'LKR',
    status ENUM('PENDING','REDIRECTED','PAID','FAILED','CANCELLED') DEFAULT 'PENDING',
    redirect_url VARCHAR(500) NULL COMMENT 'For online gateways',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (method_id) REFERENCES payment_methods(id),
    INDEX idx_order_id (order_id),
    INDEX idx_method_id (method_id),
    INDEX idx_gateway_transaction_id (gateway_transaction_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

