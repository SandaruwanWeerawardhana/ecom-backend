package org.psint.beyosclothing.modules.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cart Entity
 * Table: carts in beyos_cart_db
 * Stores cart for both guest and logged-in customers
 */
@Entity
@Table(name = "carts", indexes = {
    @Index(name = "idx_uuid", columnList = "uuid"),
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_guest_id", columnList = "guest_id"),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "customer_id")
    private Long customerId; // Reference to customer (NOT FK - cross-module)

    @Column(name = "guest_id", length = 36)
    private String guestId; // For guest users (UUID stored in cookie)

    @Column(name = "promo_code_id")
    private Long promoCodeId; // Reference to promo module (NOT FK)

    @Column(name = "promo_discount", precision = 10, scale = 2)
    private BigDecimal promoDiscount;

    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", precision = 10, scale = 2)
    private BigDecimal discountTotal;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        if (discountTotal == null) {
            discountTotal = BigDecimal.ZERO;
        }
        if (total == null) {
            total = BigDecimal.ZERO;
        }
    }

    /**
     * Check if cart belongs to a guest
     */
    public boolean isGuestCart() {
        return guestId != null && customerId == null;
    }

    /**
     * Check if cart belongs to a customer
     */
    public boolean isCustomerCart() {
        return customerId != null;
    }
}

