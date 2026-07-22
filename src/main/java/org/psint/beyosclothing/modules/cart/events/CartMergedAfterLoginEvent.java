package org.psint.beyosclothing.modules.cart.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Cart Merged After Login Event
 * Published when guest cart is merged with customer cart after login
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartMergedAfterLoginEvent implements Serializable {
    private Long guestCartId;
    private String guestCartUuid;
    private String guestId;
    private Long customerCartId;
    private String customerCartUuid;
    private Long customerId;
    private Integer itemsMerged;
    private LocalDateTime mergedAt;
}

