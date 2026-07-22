package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Cart Item Entity
 * Represents items in a reseller's cart
 */
@Entity
@Table(name = "reseller_cart_items", indexes = {
    @Index(name = "idx_reseller_cart_items_uuid", columnList = "uuid"),
    @Index(name = "idx_reseller_cart_items_cart_id", columnList = "cart_id"),
    @Index(name = "idx_reseller_cart_items_product_id", columnList = "product_id"),
    @Index(name = "idx_reseller_cart_items_variant_id", columnList = "variant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "base_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseUnitPrice;

    @Column(name = "override_unit_price", precision = 10, scale = 2)
    private BigDecimal overrideUnitPrice;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "margin_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal marginAmount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    // Helper methods
    public BigDecimal calculateMargin() {
        BigDecimal sellingPrice = getEffectivePrice();
        BigDecimal costPrice = baseUnitPrice != null ? baseUnitPrice : BigDecimal.ZERO;
        BigDecimal margin = sellingPrice.subtract(costPrice);
        this.marginAmount = margin.multiply(BigDecimal.valueOf(quantity != null ? quantity : 0));
        return this.marginAmount;
    }

    public boolean hasOverride() {
        return overrideUnitPrice != null && overrideUnitPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getEffectivePrice() {
        return hasOverride() ? overrideUnitPrice : baseUnitPrice;
    }

    public void calculateTotalPrice() {
        BigDecimal effectivePrice = getEffectivePrice();
        int qty = quantity != null ? quantity : 0;
        this.totalPrice = effectivePrice.multiply(BigDecimal.valueOf(qty));
    }
}
