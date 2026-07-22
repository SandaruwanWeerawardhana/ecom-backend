-- V5__create_reseller_bank_accounts_table.sql
-- Create reseller_bank_accounts table for bank account management

CREATE TABLE reseller_bank_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    reseller_id BIGINT NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    branch_name VARCHAR(100),
    branch_code VARCHAR(20),
    swift_code VARCHAR(20) COMMENT 'Optional for international transfers',
    is_primary BOOLEAN DEFAULT FALSE COMMENT 'One primary account per reseller',
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE COMMENT 'Admin verification status',
    date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reseller_bank_accounts_uuid (uuid),
    INDEX idx_reseller_bank_accounts_reseller_id (reseller_id),
    INDEX idx_reseller_bank_accounts_is_primary (is_primary),
    INDEX idx_reseller_bank_accounts_reseller_primary (reseller_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

