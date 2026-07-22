package org.psint.beyosclothing.modules.promotions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Promotion Action Entity
 * Database: beyos_promo_db
 * Table: promotion_actions
 */
@Entity
@Table(name = "promotion_actions", indexes = {
    @Index(name = "idx_promotion_id", columnList = "promotion_id"),
    @Index(name = "idx_action_type", columnList = "action_type"),
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_category_id", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ActionType actionType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "free_product_id")
    private Long freeProductId;

    @Column(name = "free_quantity")
    private Integer freeQuantity = 1;

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
    public enum ActionType {
        APPLY_PERCENTAGE_DISCOUNT,
        APPLY_FIXED_DISCOUNT,
        FREE_PRODUCT,
        DISCOUNT_SPECIFIC_PRODUCT,
        DISCOUNT_SPECIFIC_CATEGORY
    }
}

