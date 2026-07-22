-- =====================================================
-- Table: payment_transactions
-- Purpose: Final payment records (successful money movement)
-- =====================================================

USE beyos_payment;

CREATE TABLE payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    payment_request_id BIGINT NOT NULL,
    method_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'LKR',
    gateway_transaction_id VARCHAR(100) NULL,
    status ENUM('SUCCESS','FAILED','PENDING','REFUNDED') DEFAULT 'PENDING',
    status_message VARCHAR(255) NULL,
    paid_at DATETIME NULL,
    callback_payload JSON NULL COMMENT 'Webhook/callback data from gateway',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id),
    FOREIGN KEY (method_id) REFERENCES payment_methods(id),
    INDEX idx_order_id (order_id),
    INDEX idx_payment_request_id (payment_request_id),
    INDEX idx_gateway_transaction_id (gateway_transaction_id),
    INDEX idx_status (status),
    INDEX idx_paid_at (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

