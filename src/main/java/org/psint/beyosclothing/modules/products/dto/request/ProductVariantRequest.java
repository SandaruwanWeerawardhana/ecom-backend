package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class ProductVariantRequest {

    // UUID for updating existing variant
    private String uuid;

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotBlank(message = "Attribute summary is required")
    private String attributeSummary; // e.g., "S-Blue", "L-White-Black"

    private BigDecimal weightKg;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;

    private String thumbnailImageName; // Image name returned from upload API

    private BigDecimal showcasePrice;
    private BigDecimal salePrice;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;

    private BigDecimal resellerPrice;
    private BigDecimal wholesalePrice;
    private Integer wholesaleMinQty;
    private BigDecimal productionCost;

    private Boolean isDefault = false; // For simple products or default variant

    // Stock/Inventory fields
    private Integer initialStockQuantity = 0; // Initial stock quantity for this variant
    private Boolean allowBackorder = false; // Allow backorder when out of stock
    private Integer lowStockThreshold = 10; // Low stock alert threshold

    // Inventory status (IN_STOCK, OUT_OF_STOCK, ON_BACKORDER)
    private String inventoryStatus = "IN_STOCK";

    /**
     * List of attribute value UUIDs for this variant
     * These UUIDs should correspond to the attribute values
     * after new attributes/values have been created
     * Optional for SIMPLE products without attributes
     */
    private List<String> attributeValueUuids;
}
