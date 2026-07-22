-- ============================================
-- PRODUCT DATABASE SCHEMA - beyos_product_db
-- Migration: V1__create_product_schema.sql
-- ============================================

-- ========================================
-- Product Categories Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    parent_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_parent_id (parent_id),
    INDEX idx_slug (slug),
    INDEX idx_name (name),
    FOREIGN KEY (parent_id) REFERENCES product_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Products Table (Main)
-- ========================================
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(500) NOT NULL UNIQUE,
    short_description TEXT,
    description LONGTEXT,
    category_id BIGINT NULL,
    default_variant_id BIGINT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    thumbnail_url VARCHAR(500),
    showcase_price DECIMAL(10, 2),
    sale_price DECIMAL(10, 2),
    product_type ENUM('SIMPLE', 'VARIABLE') NOT NULL DEFAULT 'SIMPLE',
    sale_start DATETIME NULL,
    sale_end DATETIME NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    visibility ENUM('PUBLIC', 'PRIVATE', 'HIDDEN') NOT NULL DEFAULT 'PUBLIC',
    sold_individually BOOLEAN NOT NULL DEFAULT FALSE,
    pricing_mode ENUM('PRODUCT_LEVEL', 'VARIANT_LEVEL') NOT NULL DEFAULT 'PRODUCT_LEVEL',
    wholesale_price DECIMAL(10, 2),
    wholesale_min_qty INT,
    production_cost DECIMAL(10, 2),
    is_publish BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_sku (sku),
    INDEX idx_slug (slug),
    INDEX idx_category_id (category_id),
    INDEX idx_product_type (product_type),
    INDEX idx_visibility (visibility),
    INDEX idx_featured (featured),
    INDEX idx_is_publish (is_publish),
    FOREIGN KEY (category_id) REFERENCES product_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Attributes Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_attributes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Attribute Values Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_attribute_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    attribute_id BIGINT NOT NULL,
    value VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_attribute_id (attribute_id),
    FOREIGN KEY (attribute_id) REFERENCES product_attributes(id) ON DELETE CASCADE,
    UNIQUE KEY unique_attribute_value (attribute_id, value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Variants Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    attribute_summary VARCHAR(500),
    weight_kg DECIMAL(10, 3),
    length_cm DECIMAL(10, 2),
    width_cm DECIMAL(10, 2),
    height_cm DECIMAL(10, 2),
    thumbnail_url VARCHAR(500),
    showcase_price DECIMAL(10, 2),
    sale_price DECIMAL(10, 2),
    sale_start DATETIME NULL,
    sale_end DATETIME NULL,
    reseller_price DECIMAL(10, 2),
    wholesale_price DECIMAL(10, 2),
    wholesale_min_qty INT,
    production_cost DECIMAL(10, 2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_sku (sku),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Variant Attribute Values (Junction Table)
-- ========================================
CREATE TABLE IF NOT EXISTS variant_attribute_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    variant_id BIGINT NOT NULL,
    attribute_value_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_variant_id (variant_id),
    INDEX idx_attribute_value_id (attribute_value_id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    FOREIGN KEY (attribute_value_id) REFERENCES product_attribute_values(id) ON DELETE CASCADE,
    UNIQUE KEY unique_variant_attribute (variant_id, attribute_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Dimension Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_dimension (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    weight_kg DECIMAL(10, 3),
    length_cm DECIMAL(10, 2),
    width_cm DECIMAL(10, 2),
    height_cm DECIMAL(10, 2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Gallery Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_gallery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    media_name VARCHAR(255) NOT NULL,
    media_type ENUM('IMAGE', 'VIDEO') NOT NULL DEFAULT 'IMAGE',
    sort_order INT NOT NULL DEFAULT 0,
    alt_text VARCHAR(255),
    mime_type VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_sort_order (sort_order),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Variant Gallery Table
-- ========================================
CREATE TABLE IF NOT EXISTS variant_gallery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    variant_id BIGINT NOT NULL,
    media_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    alt_text VARCHAR(255),
    mime_type VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_variant_id (variant_id),
    INDEX idx_sort_order (sort_order),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Meta Table (SEO)
-- ========================================
CREATE TABLE IF NOT EXISTS product_meta (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    meta_title VARCHAR(255),
    meta_description TEXT,
    canonical_url VARCHAR(500),
    meta_keywords VARCHAR(500),
    og_title VARCHAR(255),
    og_description TEXT,
    og_image VARCHAR(500),
    twitter_card VARCHAR(100),
    json_ld TEXT,
    robot_index BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated DATETIME,

    INDEX idx_product_id (product_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Links Table (Cross-sell, Upsell, Related)
-- ========================================
CREATE TABLE IF NOT EXISTS product_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    linked_id BIGINT NOT NULL,
    link_type ENUM('UPSELL', 'CROSSSELL', 'RELATED') NOT NULL DEFAULT 'RELATED',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_linked_id (linked_id),
    INDEX idx_link_type (link_type),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (linked_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Tags Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_slug (slug),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Tag Map (Junction Table)
-- ========================================
CREATE TABLE IF NOT EXISTS product_tag_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_tag_id (tag_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES product_tags(id) ON DELETE CASCADE,
    UNIQUE KEY unique_product_tag (product_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Price History Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    old_price DECIMAL(10, 2),
    new_price DECIMAL(10, 2),
    changed_by VARCHAR(100),
    changed_at DATETIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_variant_id (variant_id),
    INDEX idx_changed_at (changed_at),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Product Rating Aggregates Table
-- ========================================
CREATE TABLE IF NOT EXISTS product_rating_aggregates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    review_count INT NOT NULL DEFAULT 0,
    rating_total DECIMAL(10, 2) NOT NULL DEFAULT 0,
    rating_average DECIMAL(3, 2) NOT NULL DEFAULT 0,
    rating_1_count INT NOT NULL DEFAULT 0,
    rating_2_count INT NOT NULL DEFAULT 0,
    rating_3_count INT NOT NULL DEFAULT 0,
    rating_4_count INT NOT NULL DEFAULT 0,
    rating_5_count INT NOT NULL DEFAULT 0,
    last_updated DATETIME,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    INDEX idx_rating_average (rating_average),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Add Foreign Key for default_variant_id in products
-- (Must be added after product_variants table exists)
-- ========================================
ALTER TABLE products
ADD CONSTRAINT fk_products_default_variant
FOREIGN KEY (default_variant_id) REFERENCES product_variants(id) ON DELETE SET NULL;
