package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private String uuid;
    private String sku;
    private String title;
    private String slug;
    private String shortDescription;
    private String description;

    private CategoryResponse category;

    private String thumbnailUrl;

    private String showcasePrice;
    private String salePrice;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;

    private String productType; // SIMPLE, VARIABLE
    private Boolean featured;
    private String visibility; // PUBLIC, PRIVATE, HIDDEN
    private Boolean soldIndividually;

    private String pricingMode; // PRODUCT_LEVEL, VARIANT_LEVEL

    private String wholesalePrice;
    private Integer wholesaleMinQty;
    private String productionCost;

    private Boolean isPublish;
    private Boolean isActive;
    private Boolean isResellerProduct;

    private List<ProductTagResponse> tags;
    private List<ProductGalleryResponse> galleryImages;
    private ProductMetaResponse meta;
    private ProductDimensionResponse dimension;
    private List<ProductVariantResponse> variants;
    private ProductVariantResponse defaultVariant;

    private Map<String, Map<String, String>> variantAttributesMap;
    private Map<String, Map<String, List<String>>> variantAttributesListMap;

    // Payment methods allowed for this product (name snapshots resolved from payment module)
    private List<String> paymentMethods;

    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
}
