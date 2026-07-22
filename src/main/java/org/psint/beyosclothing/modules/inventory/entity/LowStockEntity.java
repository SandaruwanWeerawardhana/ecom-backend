package org.psint.beyosclothing.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Low Stock Alerts Entity
 * Database: beyos_inventory_db
 * Table: low_stock_alerts
 */
@Entity
@Table(name = "low_stock_alerts",
    indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_alert_status", columnList = "alert_status"),
        @Index(name = "idx_notified", columnList = "notified"),
        @Index(name = "idx_date_created", columnList = "date_created")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_product_variant_alert", columnNames = {"product_id", "variant_id", "alert_status"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId; // References products.id from product module

    @Column(name = "variant_id")
    private Long variantId; // References product_variants.id from product module

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "threshold_level", nullable = false)
    private Integer thresholdLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false, length = 20)
    private AlertStatus alertStatus = AlertStatus.PENDING;

    @Column(name = "notified", nullable = false)
    private Boolean notified = false;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated", nullable = false)
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        dateUpdated = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }

    public enum AlertStatus {
        PENDING,       // Alert created but not yet acted upon
        ACKNOWLEDGED,  // Alert seen/acknowledged by staff
        RESOLVED       // Stock replenished or issue resolved
    }
}

