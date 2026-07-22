-- ============================================
-- AUTH DATABASE SCHEMA - beyos_auth_db
-- Migration: V1__create_auth_schema.sql
-- ============================================

-- User Role Table (Updated to support admin roles)
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(100) UNIQUE,
    role_name VARCHAR(200) NOT NULL UNIQUE,
    description VARCHAR(500),
    token VARCHAR(100) UNIQUE,
    user_type VARCHAR(50),
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    INDEX idx_role_name (role_name),
    INDEX idx_role_code (role_code),
    INDEX idx_token (token),
    INDEX idx_user_type (user_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- User Permission Table (Hierarchical)
-- ========================================
CREATE TABLE IF NOT EXISTS user_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    module VARCHAR(100) NOT NULL,
    parent_id BIGINT NULL,
    is_parent BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT,

    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    INDEX idx_permission_code (permission_code),
    INDEX idx_permission_name (permission_name),
    INDEX idx_parent_id (parent_id),
    INDEX idx_module (module),

    FOREIGN KEY (parent_id) REFERENCES user_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Role Permission Mapping Table
CREATE TABLE IF NOT EXISTS role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    FOREIGN KEY (role_id) REFERENCES user_role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES user_permission(id) ON DELETE CASCADE,
    UNIQUE KEY unique_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE COMMENT 'Auto-generated (e.g., USR8k3x2p9qm5n7)',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT 'Used for login',
    password VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL COMMENT 'ADMIN, CUSTOMER, RESELLER',
    user_role_id BIGINT NULL COMMENT 'For ADMIN users - references user_role table',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    login_attempts INT DEFAULT 0,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_role_id) REFERENCES user_role(id) ON DELETE SET NULL,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_user_type (user_type),
    INDEX idx_user_role_id (user_role_id),
    INDEX idx_email_verified (email_verified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Login History Table
CREATE TABLE IF NOT EXISTS login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    ip_address VARCHAR(45),
    device_info VARCHAR(255),
    user_agent VARCHAR(500),
    location_city VARCHAR(100),
    location_country VARCHAR(100),
    login_time DATETIME NOT NULL,
    is_login_success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_login_id (login_id),
    INDEX idx_login_time (login_time),
    INDEX idx_ip_address (ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Password Reset Tokens Table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_token_hash (token_hash),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Email Verification Tokens Table
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at DATETIME,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_token_hash (token_hash),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert Default User Type Roles
INSERT INTO user_role (role_code, role_name, token, user_type, is_active) VALUES
('ADMIN', 'Admin', 'ADMIN_TOKEN', 'ADMIN', TRUE),
('CUSTOMER', 'Customer', 'CUSTOMER_TOKEN', 'CUSTOMER', TRUE),
('RESELLER', 'Reseller', 'RESELLER_TOKEN', 'RESELLER', TRUE)
ON DUPLICATE KEY UPDATE role_name = role_name;

-- ============================================
-- NOTE: Hierarchical permissions are now initialized by the application
-- via AdminEventConsumer.initializePermissions() method
-- Trigger initialization via: POST /api/admin/permissions/initialize
-- ============================================
