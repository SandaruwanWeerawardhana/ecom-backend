package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Price History Entity
 * Database: beyos_product_db
 * Table: product_price_history
 */
@Entity
@Table(name = "product_price_history", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_variant_id", columnList = "variant_id"),
    @Index(name = "idx_changed_at", columnList = "changed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "old_price", precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

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
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }
}
