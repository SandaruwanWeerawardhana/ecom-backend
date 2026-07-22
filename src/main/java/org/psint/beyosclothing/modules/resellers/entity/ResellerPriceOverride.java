package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Price Override Entity
 * Audit trail for price overrides by resellers
 */
@Entity
@Table(name = "reseller_price_overrides", indexes = {
    @Index(name = "idx_reseller_price_overrides_uuid", columnList = "uuid"),
    @Index(name = "idx_reseller_price_overrides_reseller_id", columnList = "reseller_id"),
    @Index(name = "idx_reseller_price_overrides_order_id", columnList = "order_id"),
    @Index(name = "idx_reseller_price_overrides_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerPriceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "reseller_id", nullable = false)
    private Long resellerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "base_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseUnitPrice;

    @Column(name = "override_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal overrideUnitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "margin_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal marginAmount;

    @Column(name = "margin_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal marginPercentage;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    // Helper methods
    public BigDecimal getMarginPercentage() {
        if (baseUnitPrice == null || baseUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal margin = overrideUnitPrice.subtract(baseUnitPrice);
        return margin.divide(baseUnitPrice, 4, RoundingMode.HALF_UP)
                     .multiply(BigDecimal.valueOf(100))
                     .setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isPositiveMargin() {
        return marginAmount != null && marginAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public void calculateMargin() {
        BigDecimal unitMargin = overrideUnitPrice.subtract(baseUnitPrice);
        this.marginAmount = unitMargin.multiply(BigDecimal.valueOf(quantity));
        this.marginPercentage = getMarginPercentage();
    }
}

