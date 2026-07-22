package org.psint.beyosclothing.modules.customers.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private String variantAttributeSummary;

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
