package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for Reseller Cart Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reseller cart details with all items")
public class ResellerCartResponse {

    @Schema(description = "Cart UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String cartUuid;

    @Schema(description = "Cart items")
    private List<CartItemDetail> items;

    @Schema(description = "Subtotal amount", example = "45000.00")
    private BigDecimal subtotal;

    @Schema(description = "Tax amount", example = "0.00")
    private BigDecimal taxAmount;

    @Schema(description = "Discount amount", example = "0.00")
    private BigDecimal discountAmount;

    @Schema(description = "Total amount", example = "45000.00")
    private BigDecimal total;

    @Schema(description = "Total margin/profit", example = "5000.00")
    private BigDecimal totalMargin;

    @Schema(description = "Total items count", example = "5")
    private Integer itemCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDetail {
        @Schema(description = "Cart item UUID")
        private String uuid;

        @Schema(description = "Product UUID")
        private String productUuid;

        @Schema(description = "Variant UUID")
        private String variantUuid;

        @Schema(description = "Product name")
        private String productName;

        @Schema(description = "Variant name")
        private String variantName;

        @Schema(description = "Base price (reseller purchase price)")
        private BigDecimal basePrice;

        @Schema(description = "Override price (reseller selling price)")
        private BigDecimal overridePrice;

        @Schema(description = "Effective price (override or base)")
        private BigDecimal effectivePrice;

        @Schema(description = "Quantity")
        private Integer quantity;

        @Schema(description = "Total price")
        private BigDecimal totalPrice;

        @Schema(description = "Margin amount (profit)")
        private BigDecimal margin;

        @Schema(description = "Has price override")
        private Boolean hasOverride;
    }
}

