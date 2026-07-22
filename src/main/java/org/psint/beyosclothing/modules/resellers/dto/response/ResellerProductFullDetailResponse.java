package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full Product Detail Response for Reseller (via RabbitMQ cross-module lookup)
 * Mirrors the ProductResponse structure from the Product module
 * but is owned by the Reseller module - no cross-module class dependency
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full product detail response for reseller portal")
public class ResellerProductFullDetailResponse {

    @Schema(description = "Product UUID")
    private String uuid;

    @Schema(description = "Product SKU")
    private String sku;

    @Schema(description = "Product title")
    private String title;

    @Schema(description = "URL slug")
    private String slug;

    @Schema(description = "Short description")
    private String shortDescription;

    @Schema(description = "Full description")
    private String description;

    @Schema(description = "Category info")
    private CategoryInfo category;

    @Schema(description = "Thumbnail image URL")
    private String thumbnailUrl;

    @Schema(description = "Showcase price")
    private String showcasePrice;

    @Schema(description = "Sale price")
    private String salePrice;

    @Schema(description = "Sale start date")
    private LocalDateTime saleStart;

    @Schema(description = "Sale end date")
    private LocalDateTime saleEnd;

    @Schema(description = "Product type: SIMPLE or VARIABLE")
    private String productType;

    @Schema(description = "Is featured")
    private Boolean featured;

    @Schema(description = "Visibility: PUBLIC, PRIVATE, HIDDEN")
    private String visibility;

    @Schema(description = "Sold individually flag")
    private Boolean soldIndividually;

    @Schema(description = "Pricing mode: PRODUCT_LEVEL or VARIANT_LEVEL")
    private String pricingMode;

    @Schema(description = "Wholesale price")
    private String wholesalePrice;

    @Schema(description = "Reseller price")
    private String resellerPrice;

    @Schema(description = "Wholesale minimum quantity")
    private Integer wholesaleMinQty;

    @Schema(description = "Production cost")
    private String productionCost;

    @Schema(description = "Is published")
    private Boolean isPublish;

    @Schema(description = "Is active")
    private Boolean isActive;

    @Schema(description = "Tags")
    private List<TagInfo> tags;

    @Schema(description = "Gallery images")
    private List<GalleryImageInfo> galleryImages;

    @Schema(description = "SEO meta")
    private MetaInfo meta;

    @Schema(description = "Dimensions")
    private DimensionInfo dimension;

    @Schema(description = "Variants")
    private List<VariantInfo> variants;

    @Schema(description = "Default variant")
    private VariantInfo defaultVariant;

    @Schema(description = "Variant attributes map (variantUuid -> attribute name -> single value)")
    private Map<String, Map<String, String>> variantAttributesMap;

    @Schema(description = "Variant attributes list map (variantUuid -> attribute name -> list of values)")
    private Map<String, Map<String, List<String>>> variantAttributesListMap;

    @Schema(description = "Date created")
    private LocalDateTime dateCreated;

    @Schema(description = "Date updated")
    private LocalDateTime dateUpdated;

    // ── Reseller-specific additions ──────────────────────────────────────

    @Schema(description = "Reseller markup rules")
    private MarkupRules markupRules;

    // ── Nested DTOs ───────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private String uuid;
        private String name;
        private String slug;
        private String parentCategoryName;
        private String parentCategoryUuid;
        private Boolean isActive;
        private List<String> subCategories;
        private LocalDateTime dateCreated;
        private LocalDateTime dateUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagInfo {
        private String uuid;
        private String name;
        private String slug;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GalleryImageInfo {
        private String uuid;
        private String mediaUrl;
        private Integer sortOrder;
        private String altText;
        private String mimeType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaInfo {
        private String uuid;
        private String metaTitle;
        private String metaDescription;
        private String canonicalUrl;
        private String metaKeywords;
        private String ogTitle;
        private String ogDescription;
        private String ogImage;
        private String twitterCard;
        private String jsonLd;
        private Boolean robotIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionInfo {
        private String uuid;
        private String weightKg;
        private String lengthCm;
        private String widthCm;
        private String heightCm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantInfo {
        private String uuid;
        private String sku;
        private String attributeSummary;
        private String weightKg;
        private String lengthCm;
        private String widthCm;
        private String heightCm;
        private String thumbnailUrl;
        private String showcasePrice;
        private String salePrice;
        private LocalDateTime saleStart;
        private LocalDateTime saleEnd;
        private String resellerPrice;
        private String wholesalePrice;
        private Integer wholesaleMinQty;
        private String productionCost;
        private Boolean isActive;
        private List<AttributeValueInfo> attributeValues;
        private List<GalleryImageInfo> galleryImages;
        private LocalDateTime dateCreated;
        private LocalDateTime dateUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeValueInfo {
        private String uuid;
        private String value;
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarkupRules {
        private Boolean allowPriceOverride;
        private BigDecimal minMarkupPercentage;
        private BigDecimal maxMarkupPercentage;
    }
}

