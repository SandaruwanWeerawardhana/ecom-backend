package org.psint.beyosclothing.modules.cart.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Cart Item Removed Event
 * Published when an item is removed from cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemRemovedEvent implements Serializable {
    private Long cartId;
    private String cartUuid;
    private Long customerId;
    private String guestId;
    private Long productId;
    private Long variantId;
    private LocalDateTime removedAt;
}

