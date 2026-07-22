package org.psint.beyosclothing.modules.promotions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Promotion Product Map Entity - Map promotions to specific products
 * Database: beyos_promo_db
 * Table: promotion_product_map
 */
@Entity
@Table(name = "promotion_product_map", indexes = {
    @Index(name = "idx_promotion_id", columnList = "promotion_id"),
    @Index(name = "idx_product_id", columnList = "product_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "unique_promotion_product", columnNames = {"promotion_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionProductMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

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
}

