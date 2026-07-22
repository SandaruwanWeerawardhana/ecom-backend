package org.psint.beyosclothing.modules.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cart Item Entity
 * Table: cart_items in beyos_cart_db
 * Stores individual items in a cart
 */
@Entity
@Table(name = "cart_items", indexes = {
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
public class CartItemEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "cart_id", nullable = false)
    private Long cartId; // FK to carts table

    @Column(name = "product_id", nullable = false)
    private Long productId; // Reference to product module (NOT FK - cross-module)

    @Column(name = "variant_id")
    private Long variantId; // Reference to product variant (NOT FK - cross-module)

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price_snapshot", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceSnapshot; // Price at time added

    @Column(name = "sale_price_snapshot", precision = 10, scale = 2)
    private BigDecimal salePriceSnapshot; // Sale price at time added

    @Column(name = "stock_available")
    private Integer stockAvailable; // Stock snapshot for validation

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (quantity == null) {
            quantity = 1;
        }
    }

    /**
     * Calculate line total based on price snapshot
     */
    public BigDecimal getLineTotal() {
        BigDecimal effectivePrice = salePriceSnapshot != null ? salePriceSnapshot : priceSnapshot;
        return effectivePrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Check if item has sale price
     */
    public boolean isOnSale() {
        return salePriceSnapshot != null && salePriceSnapshot.compareTo(priceSnapshot) < 0;
    }
}

