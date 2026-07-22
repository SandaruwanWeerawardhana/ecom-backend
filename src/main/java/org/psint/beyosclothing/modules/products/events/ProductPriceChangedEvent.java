package org.psint.beyosclothing.modules.products.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Price Changed Event
 * Published when product price is changed
 * Consumed by Inventory module to update stock records if needed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceChangedEvent implements Serializable {
    private Long productId;
    private Long variantId;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private String changedBy;
    private LocalDateTime changedAt;
}
