-- =====================================================
-- Table: pos_terminals
-- Purpose: Physical POS devices and counters management
-- =====================================================

CREATE DATABASE IF NOT EXISTS beyos_pos;
USE beyos_pos;

CREATE TABLE pos_terminals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL COMMENT 'Public terminal UUID',
    name VARCHAR(100) NOT NULL COMMENT 'Terminal name: Counter 1, Mobile POS',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT 'Unique terminal code: TERM-001',
    location VARCHAR(255) NULL COMMENT 'Physical location: Floor 1, Section A',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Terminal active status',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_code (code),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='POS terminal devices and counters';

