package org.psint.beyosclothing.modules.products.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Updated Event
 * Published when a product is updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdatedEvent implements Serializable {
    private Long productId;
    private String uuid;
    private String sku;
    private String title;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal salePrice;
    private Integer stockAvailable;
    private String thumbnailUrl;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
