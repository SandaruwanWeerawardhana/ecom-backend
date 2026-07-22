package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Product Card Response DTO
 * Used for displaying products in website home page and listing pages
 * Contains minimal information needed for product cards
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardResponse {
    private String uuid;
    private String title;
    private String slug;
    private String thumbnailUrl;

    // Pricing
    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private Integer discountPercentage; // e.g., 20 for 20% off

    // Rating & Reviews
    private Integer reviewCount;
    private BigDecimal averageRating; // 0.0 to 5.0

    // Additional info
    private Boolean featured;
    private Boolean isPublish;
}

