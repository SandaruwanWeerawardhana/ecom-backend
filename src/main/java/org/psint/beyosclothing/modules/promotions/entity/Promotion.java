package org.psint.beyosclothing.modules.promotions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Promotion Entity - Main promotions and discount codes
 * Database: beyos_promo_db
 * Table: promotions
 */
@Entity
@Table(name = "promotions", indexes = {
    @Index(name = "idx_promo_code", columnList = "promo_code"),
    @Index(name = "idx_is_active", columnList = "is_active"),
    @Index(name = "idx_start_at", columnList = "start_at"),
    @Index(name = "idx_end_at", columnList = "end_at"),
    @Index(name = "idx_discount_type", columnList = "discount_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "promo_code", unique = true, length = 50)
    private String promoCode;

    @Column(name = "is_code_required", nullable = false)
    private Boolean isCodeRequired = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "is_stackable", nullable = false)
    private Boolean isStackable = false;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_limit_per_user")
    private Integer usageLimitPerUser;

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
    public enum DiscountType {
        PERCENTAGE,
        FIXED,
        FREE_PRODUCT,
        BOGO
    }
}

