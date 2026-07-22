-- =====================================================
-- Table: payment_logs
-- Purpose: Log all gateway API calls, callbacks, webhooks
-- =====================================================

USE beyos_payment;

CREATE TABLE payment_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    method_id BIGINT NULL,
    payment_request_id BIGINT NULL,
    transaction_id BIGINT NULL,
    type ENUM('REQUEST','RESPONSE','CALLBACK') NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (method_id) REFERENCES payment_methods(id) ON DELETE SET NULL,
    FOREIGN KEY (payment_request_id) REFERENCES payment_requests(id) ON DELETE SET NULL,
    FOREIGN KEY (transaction_id) REFERENCES payment_transactions(id) ON DELETE SET NULL,
    INDEX idx_method_id (method_id),
    INDEX idx_payment_request_id (payment_request_id),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

