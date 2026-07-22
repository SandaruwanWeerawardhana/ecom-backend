package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS Cart Item Entity
 * Database: beyos_pos
 * Table: pos_cart_items
 * Represents individual products in POS carts
 */
@Entity
@Table(name = "pos_cart_items", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_cart_id", columnList = "cart_id"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_variant_id", columnList = "variant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class PosCartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId; // NULL for simple products

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "stock_available", nullable = false)
    private Integer stockAvailable = 0; // Snapshot when item added

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }

        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Get line total (unit price × quantity)
     */
    public BigDecimal getLineTotal() {
        if (Boolean.TRUE.equals(isActive) && unitPrice != null && quantity != null) {
            log.info("Calculating line total: unitPrice={} × quantity={}", unitPrice, quantity);
            return unitPrice.multiply(new BigDecimal(quantity));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if stock is sufficient for requested quantity
     */
    public boolean isStockSufficient() {
        return stockAvailable >= quantity;
    }
}
