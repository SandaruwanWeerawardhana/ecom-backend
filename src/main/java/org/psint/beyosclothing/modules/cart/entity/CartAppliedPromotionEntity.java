package org.psint.beyosclothing.modules.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cart Applied Promotion Entity
 * Table: cart_applied_promotions in beyos_cart_db
 * Stores individual promotion rules applied to cart
 */
@Entity
@Table(name = "cart_applied_promotions", indexes = {
    @Index(name = "idx_uuid", columnList = "uuid"),
    @Index(name = "idx_cart_id", columnList = "cart_id"),
    @Index(name = "idx_promo_code_id", columnList = "promo_code_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartAppliedPromotionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "cart_id", nullable = false)
    private Long cartId; // FK to carts table

    @Column(name = "promo_code_id")
    private Long promoCodeId; // Reference to promo module (NOT FK - cross-module)

    @Column(name = "rule_id")
    private Long ruleId; // Reference to specific discount rule (NOT FK - cross-module)

    @Column(name = "discount_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal discountAmount;

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }
}

