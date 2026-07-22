package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for detailed product information with reseller pricing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed product information with reseller pricing")
public class ResellerProductDetailResponse {

    @Schema(description = "Product UUID", example = "abc123-def456")
    private String productUuid;

    @Schema(description = "Product name", example = "Cotton T-Shirt")
    private String name;

    @Schema(description = "Product description")
    private String description;

    @Schema(description = "Product type: SIMPLE or VARIABLE")
    private String productType;

    @Schema(description = "Category UUID")
    private String categoryUuid;

    @Schema(description = "Category name", example = "T-Shirts")
    private String categoryName;

    @Schema(description = "Thumbnail URL", example = "/uploads/products/thumbnails/product-123.jpg")
    private String thumbnailUrl;

    @Schema(description = "Gallery images")
    private List<String> galleryImages;

    @Schema(description = "Product attributes (for VARIABLE products)")
    private List<ProductAttribute> attributes;

    @Schema(description = "Product variants with reseller pricing")
    private List<ResellerVariantInfo> variants;

    @Schema(description = "Reseller's allowed markup range")
    private MarkupRules markupRules;

    @Schema(description = "Is product available", example = "true")
    private Boolean isAvailable;

    /**
     * Product attribute information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Product attribute (size, color, etc.)")
    public static class ProductAttribute {
        @Schema(description = "Attribute name", example = "Size")
        private String name;

        @Schema(description = "Attribute values", example = "[\"S\", \"M\", \"L\", \"XL\"]")
        private List<String> values;
    }

    /**
     * Variant information with reseller pricing
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Product variant with reseller pricing")
    public static class ResellerVariantInfo {
        @Schema(description = "Variant UUID")
        private String variantUuid;

        @Schema(description = "SKU", example = "TSH-BLK-M")
        private String sku;

        @Schema(description = "Attribute summary", example = "Size: M, Color: Black")
        private String attributeSummary;

        @Schema(description = "Customer showcase price", example = "2500.00")
        private BigDecimal showcasePrice;

        @Schema(description = "Reseller base price", example = "2000.00")
        private BigDecimal resellerPrice;

        @Schema(description = "Suggested selling price", example = "2500.00")
        private BigDecimal suggestedSellingPrice;

        @Schema(description = "Stock available", example = "50")
        private Integer stockAvailable;

        @Schema(description = "Is variant available", example = "true")
        private Boolean isAvailable;

        @Schema(description = "Variant thumbnail URL")
        private String thumbnailUrl;
    }

    /**
     * Reseller's markup rules
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Reseller's allowed markup rules")
    public static class MarkupRules {
        @Schema(description = "Is price override allowed", example = "true")
        private Boolean allowPriceOverride;

        @Schema(description = "Minimum allowed markup percentage", example = "10.00")
        private BigDecimal minMarkupPercentage;

        @Schema(description = "Maximum allowed markup percentage", example = "50.00")
        private BigDecimal maxMarkupPercentage;
    }
}

