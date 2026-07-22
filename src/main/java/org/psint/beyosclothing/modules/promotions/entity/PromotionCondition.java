package org.psint.beyosclothing.modules.promotions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Promotion Condition Entity
 * Database: beyos_promo_db
 * Table: promotion_conditions
 */
@Entity
@Table(name = "promotion_conditions", indexes = {
    @Index(name = "idx_promotion_id", columnList = "promotion_id"),
    @Index(name = "idx_condition_type", columnList = "condition_type"),
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_category_id", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 30)
    private ConditionType conditionType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "min_quantity")
    private Integer minQuantity;

    @Column(name = "min_amount", precision = 10, scale = 2)
    private BigDecimal minAmount;

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

    // Enums
    public enum ConditionType {
        MIN_CART_TOTAL,
        MIN_ITEM_QUANTITY,
        MIN_CATEGORY_ITEM_COUNT,
        MIN_PRODUCT_QUANTITY,
        MIN_TOTAL_ITEMS,
        BUY_X_GET_Y_TRIGGER
    }
}

