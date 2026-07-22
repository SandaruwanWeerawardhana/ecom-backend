-- V6__create_reseller_withdrawal_requests_table.sql
-- Create reseller_withdrawal_requests table for withdrawal management

CREATE TABLE reseller_withdrawal_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    reseller_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING',
    requested_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_date DATETIME COMMENT 'When admin acts on the request',
    completed_date DATETIME COMMENT 'When payment is made',
    processed_by_admin_id BIGINT COMMENT 'Admin who processed the request',
    rejection_reason TEXT,
    admin_notes TEXT,
    transaction_reference VARCHAR(100) COMMENT 'Bank transfer reference',
    balance_before DECIMAL(12,2) COMMENT 'Balance snapshot before withdrawal',
    balance_after DECIMAL(12,2) COMMENT 'Balance snapshot after withdrawal',
    date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reseller_withdrawal_requests_uuid (uuid),
    INDEX idx_reseller_withdrawal_requests_reseller_id (reseller_id),
    INDEX idx_reseller_withdrawal_requests_status (status),
    INDEX idx_reseller_withdrawal_requests_requested_date (requested_date),
    INDEX idx_reseller_withdrawal_requests_reseller_status (reseller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

