package org.psint.beyosclothing.modules.products.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Created Event
 * Published when a new product is created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreatedEvent implements Serializable {
    private Long productId;
    private String uuid;
    private String sku;
    private String title;
    private String slug;
    private Long brandId;
    private Long categoryId;
    private String productType;
    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private Integer initialStockQuantity;
    private Integer stockAvailable;
    private String thumbnailUrl;
    private Boolean hasVariants;
    private String createdBy;
    private LocalDateTime createdAt;
}
