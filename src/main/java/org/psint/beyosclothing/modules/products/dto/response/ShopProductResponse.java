package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Shop Product Response DTO
 * Optimized response for shop page product listing with filtering
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopProductResponse {

    private String uuid;
    private String title;
    private String thumbnailUrl;
    private String slug;
    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private Integer discountPercentage; // Calculated discount percentage from promotions

    // Dummy data for ratings (will be implemented later)
    private Double rating; // e.g., 4.6
    private Long reviewCount; // e.g., 1234
}

