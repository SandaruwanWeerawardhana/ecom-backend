package org.psint.beyosclothing.modules.cart.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cart Promo Applied Event
 * Published when a promo code is applied to cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartPromoAppliedEvent implements Serializable {
    private Long cartId;
    private String cartUuid;
    private Long customerId;
    private String guestId;
    private Long promoCodeId;
    private String promoCode;
    private BigDecimal discountAmount;
    private LocalDateTime appliedAt;
}

