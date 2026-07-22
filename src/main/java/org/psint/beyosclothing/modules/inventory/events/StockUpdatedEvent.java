package org.psint.beyosclothing.modules.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Stock Updated Event
 * Published when stock quantity is updated
 * Consumed by Product module to update product availability status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdatedEvent implements Serializable {
    private Long productId;
    private Long variantId;
    private Integer oldQuantity;
    private Integer newQuantity;
    private String movementType; // IN, OUT, ADJUSTMENT, RETURN
    private String updatedBy;
    private LocalDateTime updatedAt;
}

