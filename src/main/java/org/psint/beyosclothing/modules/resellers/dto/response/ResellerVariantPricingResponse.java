package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for variant pricing information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Variant pricing information with reseller markup rules")
public class ResellerVariantPricingResponse {

    @Schema(description = "Variant UUID")
    private String variantUuid;

    @Schema(description = "Product UUID")
    private String productUuid;

    @Schema(description = "Product name", example = "Cotton T-Shirt")
    private String productName;

    @Schema(description = "SKU", example = "TSH-BLK-M")
    private String sku;

    @Schema(description = "Attribute summary", example = "Size: M, Color: Black")
    private String attributeSummary;

    @Schema(description = "Customer showcase price", example = "2500.00")
    private BigDecimal showcasePrice;

    @Schema(description = "Reseller base price (what reseller buys at)", example = "2000.00")
    private BigDecimal resellerPrice;

    @Schema(description = "Suggested margin", example = "500.00")
    private BigDecimal suggestedMargin;

    @Schema(description = "Suggested selling price (showcase price)", example = "2500.00")
    private BigDecimal suggestedSellingPrice;

    @Schema(description = "Minimum allowed selling price", example = "2200.00")
    private BigDecimal minAllowedSellingPrice;

    @Schema(description = "Maximum allowed selling price", example = "3000.00")
    private BigDecimal maxAllowedSellingPrice;

    @Schema(description = "Stock available", example = "50")
    private Integer stockAvailable;

    @Schema(description = "Is variant available", example = "true")
    private Boolean isAvailable;

    @Schema(description = "Is price override allowed for this reseller", example = "true")
    private Boolean allowPriceOverride;

    @Schema(description = "Minimum allowed markup percentage", example = "10.00")
    private BigDecimal minMarkupPercentage;

    @Schema(description = "Maximum allowed markup percentage", example = "50.00")
    private BigDecimal maxMarkupPercentage;

    @Schema(description = "Currency", example = "LKR")
    private String currency;

    @Schema(description = "Variant thumbnail URL")
    private String thumbnailUrl;
}

