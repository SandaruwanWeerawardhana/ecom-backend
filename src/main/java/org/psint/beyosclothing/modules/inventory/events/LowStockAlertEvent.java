package org.psint.beyosclothing.modules.inventory.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Low Stock Alert Event
 * Published when stock reaches low threshold
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertEvent implements Serializable {
    private Long productId;
    private Long variantId;
    private String productSku;
    private Integer currentStock;
    private Integer threshold;
    private LocalDateTime alertedAt;
}

