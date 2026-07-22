package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Response DTO for product details lookup by ID
 * Contains minimal product information for cart display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailsLookupResponse implements Serializable {

    private String requestId;
    private Boolean found;
    private String errorMessage;

    // Product details
    private Long productId;
    private String productUuid;
    private String productTitle;
    private String productSku;
    private String thumbnailUrl;
    private String productSlug;

    // Variant details (if applicable)
    private Long variantId;
    private String variantUuid;
    private String variantSku;
    private String variantAttributeSummary; // e.g., "Size: L, Color: Blue"
    private String variantThumbnailUrl;

    // Pricing (already in cart item as snapshot, but useful for display)
    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private Boolean isOnSale;

    // Availability
    private Boolean isActive;
    private String productStatus;
    
    // Dimensions & Weight (NEW - for shipping calculation)
    private BigDecimal weightKg;        // Weight in kilograms
    private BigDecimal lengthCm;        // Length in centimeters
    private BigDecimal widthCm;         // Width in centimeters
    private BigDecimal heightCm;        // Height in centimeters
    
    // Category (NEW - for category-based promotions)
    private Long categoryId;            // Product category ID for promotion validation
}