package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for paginated reseller product list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated product list response with reseller pricing")
public class ResellerProductListResponse {

    @Schema(description = "List of products")
    private List<ResellerProductSummary> products;

    @Schema(description = "Current page number", example = "0")
    private Integer currentPage;

    @Schema(description = "Total number of pages", example = "10")
    private Integer totalPages;

    @Schema(description = "Total number of products", example = "95")
    private Long totalProducts;

    @Schema(description = "Page size", example = "20")
    private Integer pageSize;

    /**
     * Product summary for list view
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Product summary with reseller pricing")
    public static class ResellerProductSummary {

        @Schema(description = "Product UUID", example = "abc123-def456")
        private String productUuid;

        @Schema(description = "Product SKU", example = "TSHIRT-001")
        private String sku;

        @Schema(description = "Product name", example = "Cotton T-Shirt")
        private String name;

        @Schema(description = "Product description")
        private String description;

        @Schema(description = "Thumbnail URL", example = "/uploads/products/thumbnails/product-123.jpg")
        private String thumbnailUrl;

        @Schema(description = "Product type: SIMPLE or VARIABLE")
        private String productType;

        @Schema(description = "Category name", example = "T-Shirts")
        private String categoryName;

        @Schema(description = "Customer showcase price", example = "2500.00")
        private BigDecimal showcasePrice;

        @Schema(description = "Reseller base price (what reseller pays)", example = "2000.00")
        private BigDecimal resellerPrice;

        @Schema(description = "Suggested margin", example = "500.00")
        private BigDecimal suggestedMargin;

        @Schema(description = "Suggested margin percentage", example = "25.00")
        private BigDecimal suggestedMarginPercentage;

        @Schema(description = "Stock availability", example = "50")
        private Integer stockAvailable;

        @Schema(description = "Is product available", example = "true")
        private Boolean isAvailable;

        @Schema(description = "Number of variants (for VARIABLE products)", example = "3")
        private Integer variantCount;

        @Schema(description = "Price range (for VARIABLE products)", example = "2000.00 - 3000.00")
        private String priceRange;
    }
}

