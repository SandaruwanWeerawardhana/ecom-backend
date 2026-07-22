-- =====================================================
-- Table: order_returns
-- Purpose: Handle return/refund requests
-- =====================================================

USE beyos_order;

CREATE TABLE order_returns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NULL COMMENT 'FK to order_items (NULL = full order return)',

    reason VARCHAR(255) NOT NULL,
    status ENUM('REQUESTED','APPROVED','REJECTED','REFUNDED') DEFAULT 'REQUESTED',

    refund_amount DECIMAL(10,2) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE SET NULL,
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

