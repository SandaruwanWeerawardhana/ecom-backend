-- =====================================================
-- Table: shipment_tracking_events
-- Purpose: Log tracking events from courier API or manual updates
-- =====================================================

USE beyos_delivery;

CREATE TABLE shipment_tracking_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    shipment_id BIGINT NOT NULL,
    event_time DATETIME NOT NULL,
    status VARCHAR(100) NOT NULL,
    location VARCHAR(255) NULL,
    description VARCHAR(500) NULL,
    raw_payload JSON NULL COMMENT 'Raw event data from courier API',
    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE,
    INDEX idx_shipment_id (shipment_id),
    INDEX idx_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

