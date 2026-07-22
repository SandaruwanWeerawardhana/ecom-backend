-- ============================================
-- PROMOTION DATABASE SCHEMA - beyos_promo_db
-- Migration: V1__create_promotion_schema.sql
-- ============================================

-- ========================================
-- Promotions Table (Main)
-- ========================================
CREATE TABLE IF NOT EXISTS promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    promo_code VARCHAR(50) UNIQUE,
    is_code_required BOOLEAN NOT NULL DEFAULT TRUE,
    discount_type ENUM('PERCENTAGE', 'FIXED', 'FREE_PRODUCT', 'BOGO') NOT NULL,
    discount_value DECIMAL(10, 2),
    start_at DATETIME NOT NULL,
    end_at DATETIME,
    is_stackable BOOLEAN NOT NULL DEFAULT FALSE,
    usage_limit INT COMMENT 'Total usage limit across all users',
    usage_limit_per_user INT COMMENT 'Max usage per customer',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_promo_code (promo_code),
    INDEX idx_is_active (is_active),
    INDEX idx_start_at (start_at),
    INDEX idx_end_at (end_at),
    INDEX idx_discount_type (discount_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Promotion Conditions Table
-- ========================================
CREATE TABLE IF NOT EXISTS promotion_conditions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    promotion_id BIGINT NOT NULL,
    description TEXT,
    condition_type ENUM(
        'MIN_CART_TOTAL',
        'MIN_ITEM_QUANTITY',
        'MIN_CATEGORY_ITEM_COUNT',
        'MIN_PRODUCT_QUANTITY',
        'MIN_TOTAL_ITEMS',
        'BUY_X_GET_Y_TRIGGER'
    ) NOT NULL,
    product_id BIGINT NULL COMMENT 'References products.id from product module',
    category_id BIGINT NULL COMMENT 'References product_categories.id from product module',
    min_quantity INT,
    min_amount DECIMAL(10, 2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_promotion_id (promotion_id),
    INDEX idx_condition_type (condition_type),
    INDEX idx_product_id (product_id),
    INDEX idx_category_id (category_id),

    CONSTRAINT fk_conditions_promotion FOREIGN KEY (promotion_id)
        REFERENCES promotions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Promotion Actions Table
-- ========================================
CREATE TABLE IF NOT EXISTS promotion_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    promotion_id BIGINT NOT NULL,
    action_type ENUM(
        'APPLY_PERCENTAGE_DISCOUNT',
        'APPLY_FIXED_DISCOUNT',
        'FREE_PRODUCT',
        'DISCOUNT_SPECIFIC_PRODUCT',
        'DISCOUNT_SPECIFIC_CATEGORY'
    ) NOT NULL,
    product_id BIGINT NULL COMMENT 'Target product for discount',
    category_id BIGINT NULL COMMENT 'Target category for discount',
    discount_value DECIMAL(10, 2),
    free_product_id BIGINT NULL COMMENT 'Product to give free in BOGO',
    free_quantity INT DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_promotion_id (promotion_id),
    INDEX idx_action_type (action_type),
    INDEX idx_product_id (product_id),
    INDEX idx_category_id (category_id),

    CONSTRAINT fk_actions_promotion FOREIGN KEY (promotion_id)
        REFERENCES promotions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Promotion Usage Tracking Table
-- ========================================
CREATE TABLE IF NOT EXISTS promotion_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    promotion_id BIGINT NOT NULL,
    customer_id BIGINT NULL COMMENT 'References customers.id from customer module',
    order_id BIGINT NULL COMMENT 'References orders.id from order module',
    used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_promotion_id (promotion_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_order_id (order_id),
    INDEX idx_used_at (used_at),

    CONSTRAINT fk_usage_promotion FOREIGN KEY (promotion_id)
        REFERENCES promotions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Promotion Product Map (For Specific Products)
-- ========================================
CREATE TABLE IF NOT EXISTS promotion_product_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    promotion_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL COMMENT 'References products.id from product module',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_promotion_id (promotion_id),
    INDEX idx_product_id (product_id),
    UNIQUE KEY unique_promotion_product (promotion_id, product_id),

    CONSTRAINT fk_product_map_promotion FOREIGN KEY (promotion_id)
        REFERENCES promotions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Promotion Category Map (For Specific Categories)
-- ========================================
CREATE TABLE IF NOT EXISTS promotion_category_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    promotion_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL COMMENT 'References product_categories.id from product module',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_promotion_id (promotion_id),
    INDEX idx_category_id (category_id),
    UNIQUE KEY unique_promotion_category (promotion_id, category_id),

    CONSTRAINT fk_category_map_promotion FOREIGN KEY (promotion_id)
        REFERENCES promotions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Comments for clarity
-- ========================================
ALTER TABLE promotions COMMENT = 'Main promotions and discount codes';
ALTER TABLE promotion_conditions COMMENT = 'Conditions that must be met for promotion to apply';
ALTER TABLE promotion_actions COMMENT = 'Actions to perform when promotion is applied';
ALTER TABLE promotion_usage COMMENT = 'Track promotion usage per customer/order';
ALTER TABLE promotion_product_map COMMENT = 'Map promotions to specific products';
ALTER TABLE promotion_category_map COMMENT = 'Map promotions to specific categories';

