package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotBlank(message = "Product title is required")
    private String title;

    @NotBlank(message = "Slug is required")
    private String slug; // Generated from frontend, validated here

    private String shortDescription;
    private String description;

    @NotBlank(message = "Category UUID is required")
    private String categoryUuid; // Frontend sends category UUID

    private String thumbnailImageName; // Image name returned from upload API

    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;

    @NotBlank(message = "Product type is required")
    private String productType; // "SIMPLE" or "VARIABLE"

    private Boolean featured = false;
    private String visibility = "PUBLIC"; // PUBLIC, PRIVATE, HIDDEN
    private Boolean soldIndividually = false;

    private String pricingMode = "PRODUCT_LEVEL"; // PRODUCT_LEVEL or VARIANT_LEVEL

    private BigDecimal wholesalePrice;
    private Integer wholesaleMinQty;
    private BigDecimal productionCost;
    private BigDecimal resellerPrice;

    private Boolean isPublish = false;

    // Initial stock quantity for SIMPLE products
    private Integer initialStockQuantity = 0;

    // Inventory status for the product (IN_STOCK, OUT_OF_STOCK, ON_BACKORDER)
    private String inventoryStatus = "IN_STOCK";

    // Optional: Product Tags (UUIDs)
    private List<String> tagUuids;

    // Optional: Linked Products (Cross-sell, Upsell, Related)
    @Valid
    private List<ProductLinkRequest> linkedProducts;

    // Optional: Product Gallery (image names from upload API)
    private List<ProductGalleryRequest> galleryImages;

    // Optional: Product Meta (SEO)
    private ProductMetaRequest meta;

    // Optional: Product Dimension
    private ProductDimensionRequest dimension;

    /**
     * Optional: Attribute selections with support for new attributes/values
     * Frontend will send this for VARIABLE products or SIMPLE products with attributes
     * Admin can create new attributes and values on the fly
     */
    @Valid
    private List<AttributeSelectionRequest> attributeSelections;

    // Required: At least one variant (for both SIMPLE and VARIABLE)
//    @NotNull(message = "At least one variant is required")
    @Valid
    private List<ProductVariantRequest> variants;

    // Optional: Payment methods allowed for this product (list of payment method UUIDs from payment module)
    private List<String> paymentMethodUuids;

    // Whether this product is available for resellers to sell
    private Boolean isResellerProduct = false;
}
