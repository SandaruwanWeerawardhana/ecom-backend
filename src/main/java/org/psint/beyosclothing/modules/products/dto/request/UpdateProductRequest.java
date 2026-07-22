package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.Valid;
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
public class UpdateProductRequest {

    private String title;
    private String slug;
    private String shortDescription;
    private String description;
    private String categoryUuid;

    private String thumbnailImageName;

    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;

    private Boolean featured;
    private String visibility;
    private Boolean soldIndividually;

    private String pricingMode;

    private BigDecimal wholesalePrice;
    private Integer wholesaleMinQty;
    private BigDecimal productionCost;

    private Boolean isPublish;

    // Initial stock quantity for SIMPLE products
    private Integer initialStockQuantity;

    // Inventory status for the product (IN_STOCK, OUT_OF_STOCK, ON_BACKORDER)
    private String inventoryStatus;

    private List<String> tagUuids;

    // Linked Products (Cross-sell, Upsell, Related)
    @Valid
    private List<ProductLinkRequest> linkedProducts;

    private List<ProductGalleryRequest> galleryImages;
    private ProductMetaRequest meta;
    private ProductDimensionRequest dimension;

    @Valid
    private List<ProductVariantRequest> variants;

    private List<String> paymentMethodUuids;

    private Boolean isResellerProduct;
}
