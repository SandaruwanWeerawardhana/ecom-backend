package org.psint.beyosclothing.modules.cart.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cart Item Added Event
 * Published when an item is added to cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemAddedEvent implements Serializable {
    private Long cartId;
    private String cartUuid;
    private Long customerId;
    private String guestId;
    private Long productId;
    private Long variantId;
    private Integer quantity;
    private BigDecimal price;
    private LocalDateTime addedAt;
}

