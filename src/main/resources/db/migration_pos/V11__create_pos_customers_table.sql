-- ============================================
-- POS CUSTOMERS TABLE
-- Migration: V11__create_pos_customers_table.sql
-- ============================================
USE beyos_pos;

CREATE TABLE IF NOT EXISTS pos_customers (
    uuid CHAR(36) NOT NULL PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100),
    postal_code VARCHAR(20),
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY ux_pos_customers_phone (phone),
    INDEX idx_pos_customers_full_name (full_name),
    INDEX idx_pos_customers_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

