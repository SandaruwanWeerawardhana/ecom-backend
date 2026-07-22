package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for Reseller Product Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product details with reseller-specific pricing")
public class ResellerProductResponse {

    @Schema(description = "Product UUID")
    private String productUuid;

    @Schema(description = "Product name")
    private String productName;

    @Schema(description = "Product description")
    private String description;

    @Schema(description = "Product type (SIMPLE/VARIABLE)")
    private String productType;

    @Schema(description = "Reseller price for simple products")
    private BigDecimal resellerPrice;

    @Schema(description = "Showcase price (recommended retail price)")
    private BigDecimal showcasePrice;

    @Schema(description = "Stock available")
    private Integer stockAvailable;

    @Schema(description = "Product image URL")
    private String imageUrl;

    @Schema(description = "Variants for variable products")
    private List<VariantDetail> variants;

    @Schema(description = "Delivery charges")
    private BigDecimal deliveryCharges;

    @Schema(description = "Suggested markup range")
    private MarkupRange suggestedMarkupRange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantDetail {
        @Schema(description = "Variant UUID")
        private String variantUuid;

        @Schema(description = "Variant name")
        private String variantName;

        @Schema(description = "Reseller price for this variant")
        private BigDecimal resellerPrice;

        @Schema(description = "Showcase price")
        private BigDecimal showcasePrice;

        @Schema(description = "Stock available")
        private Integer stockAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarkupRange {
        @Schema(description = "Minimum markup percentage")
        private BigDecimal minMarkup;

        @Schema(description = "Maximum markup percentage")
        private BigDecimal maxMarkup;
    }
}

