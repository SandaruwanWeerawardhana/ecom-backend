package org.psint.beyosclothing.modules.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cart Item Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemResponseDTO {

    private Long id;
    private String uuid;

    // Product details
    private Long productId;
    private String productTitle;
    private String productSku;
    private String thumbnailUrl;

    // Variant details (if applicable)
    private Long variantId;
    private String variantAttributeSummary; // e.g., "Size: L, Color: Blue"

    // Pricing
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal salePrice;
    private BigDecimal lineTotal;

    // Availability
    private Integer stockAvailable;
    private Boolean isOnSale;
    private Boolean isAvailable;
}
