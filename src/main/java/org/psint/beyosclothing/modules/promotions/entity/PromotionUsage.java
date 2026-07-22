package org.psint.beyosclothing.modules.promotions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Promotion Usage Entity - Track promotion usage per customer/order
 * Database: beyos_promo_db
 * Table: promotion_usage
 */
@Entity
@Table(name = "promotion_usage", indexes = {
    @Index(name = "idx_promotion_id", columnList = "promotion_id"),
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_used_at", columnList = "used_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

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
        usedAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }
}

