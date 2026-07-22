package org.psint.beyosclothing.modules.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Stock Status Updated Event
 * Published when a product stock record's inventory status is changed.
 * Consumed by Product module to sync inventory_status on ProductVariant / Product.
 *
 * status values: IN_STOCK | OUT_OF_STOCK | ON_BACKORDER
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockStatusUpdatedEvent implements Serializable {

    private Long productId;
    private Long variantId;   // null for SIMPLE products
    private String productType; // SIMPLE | VARIABLE
    private String status;    // IN_STOCK | OUT_OF_STOCK | ON_BACKORDER
    private LocalDateTime updatedAt;
}

