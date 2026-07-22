-- =====================================================
-- Table: courier_api_logs
-- Purpose: Log all API calls to courier services for debugging
-- =====================================================

USE beyos_delivery;

CREATE TABLE courier_api_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    courier_id BIGINT NULL,
    endpoint VARCHAR(2048) NULL,
    request_payload TEXT NULL,
    response_payload TEXT NULL,
    http_status INT NULL,
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (courier_id) REFERENCES couriers(id) ON DELETE SET NULL,
    INDEX idx_courier_id (courier_id),
    INDEX idx_date_created (date_created),
    INDEX idx_http_status (http_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

