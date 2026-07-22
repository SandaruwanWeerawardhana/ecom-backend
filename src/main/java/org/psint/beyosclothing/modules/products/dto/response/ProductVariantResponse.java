package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {
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
    private Boolean isDefault;

    private Integer stockQuantity; // Fetched from beyos_inventory_db via RabbitMQ

    private List<AttributeValueResponse> attributeValues;
    private List<VariantGalleryResponse> galleryImages;

    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
}

