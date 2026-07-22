-- ================================================================
-- CART MODULE DATABASE MIGRATION
-- Database: beyos_cart_db
-- Version: V1__Initial_cart_schema.sql
-- Description: Creates all cart module tables
-- ================================================================

-- Table 1: carts
-- Main cart table for both guest and customer carts
CREATE TABLE IF NOT EXISTS carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,

    customer_id BIGINT NULL COMMENT 'Reference to customer (NOT FK - cross-module)',
    guest_id CHAR(36) NULL COMMENT 'UUID for guest users',

    promo_code_id BIGINT NULL COMMENT 'Reference to promo module',
    promo_discount DECIMAL(10,2) NULL DEFAULT 0.00,

    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    discount_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    expires_at DATETIME NULL COMMENT 'Guest cart expiration',

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_customer_id (customer_id),
    INDEX idx_guest_id (guest_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Main cart table';

-- Table 2: cart_items
-- Individual items in cart
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,

    cart_id BIGINT NOT NULL COMMENT 'FK to carts table',
    product_id BIGINT NOT NULL COMMENT 'Reference to product module',
    variant_id BIGINT NULL COMMENT 'Reference to product variant',

    quantity INT NOT NULL DEFAULT 1,

    price_snapshot DECIMAL(10,2) NOT NULL COMMENT 'Price at time added',
    sale_price_snapshot DECIMAL(10,2) NULL COMMENT 'Sale price at time added',

    stock_available INT NULL COMMENT 'Stock snapshot for validation',

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_cart_id (cart_id),
    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_is_active (is_active),

    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cart items table';

-- Table 3: cart_applied_promotions
-- Track individual promotion rules applied to cart
CREATE TABLE IF NOT EXISTS cart_applied_promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,

    cart_id BIGINT NOT NULL COMMENT 'FK to carts table',
    promo_code_id BIGINT NULL COMMENT 'Reference to promo module',
    rule_id BIGINT NULL COMMENT 'Specific discount rule applied',

    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_cart_id (cart_id),
    INDEX idx_promo_code_id (promo_code_id),
    INDEX idx_is_active (is_active),

    CONSTRAINT fk_cart_applied_promotions_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Applied promotions tracking';

-- Table 4: cart_events
-- Event log for debugging and analytics
CREATE TABLE IF NOT EXISTS cart_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,

    cart_id BIGINT NULL COMMENT 'Related cart ID',

    event_type VARCHAR(50) NOT NULL COMMENT 'ITEM_ADDED, ITEM_REMOVED, PROMO_APPLIED, CART_EXPIRED, CART_MERGED_AFTER_LOGIN',
    event_data JSON NULL COMMENT 'Event details in JSON format',

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_cart_id (cart_id),
    INDEX idx_event_type (event_type),
    INDEX idx_date_created (date_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cart events log';

-- Table 5: guest_cart_mapping
-- Secure mapping of guest session tokens to carts
CREATE TABLE IF NOT EXISTS guest_cart_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,

    guest_session_token VARCHAR(255) UNIQUE NOT NULL COMMENT 'Secure token stored in HttpOnly cookie',

    cart_id BIGINT NOT NULL COMMENT 'FK to carts table',
    cart_uuid CHAR(36) NULL COMMENT 'Cart UUID for reference',

    user_agent VARCHAR(500) NULL COMMENT 'Browser info for security',
    ip_hash VARCHAR(64) NULL COMMENT 'Hashed IP for fraud detection',
    device_fingerprint VARCHAR(255) NULL COMMENT 'Device fingerprint',

    is_merged BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'True after cart merged to customer',

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_uuid (uuid),
    INDEX idx_guest_session_token (guest_session_token),
    INDEX idx_cart_id (cart_id),
    INDEX idx_cart_uuid (cart_uuid),
    INDEX idx_is_merged (is_merged),
    INDEX idx_is_active (is_active),

    CONSTRAINT fk_guest_cart_mapping_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Guest cart security mapping';

-- ================================================================
-- END OF MIGRATION
-- ================================================================

