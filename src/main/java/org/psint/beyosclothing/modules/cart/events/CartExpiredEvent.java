package org.psint.beyosclothing.modules.cart.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Cart Expired Event
 * Published when a cart expires
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartExpiredEvent implements Serializable {
    private Long cartId;
    private String cartUuid;
    private String guestId;
    private Long customerId;
    private LocalDateTime expiredAt;
}

