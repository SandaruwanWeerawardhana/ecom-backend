package org.psint.beyosclothing.modules.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Stock Reserve Request Event
 * Published by Order module to reserve stock
 * Consumed by Inventory module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveRequestEvent implements Serializable {
    private Long orderId;
    private Long productId;
    private Long variantId;
    private Integer quantity;
    private String requestedBy;
}

