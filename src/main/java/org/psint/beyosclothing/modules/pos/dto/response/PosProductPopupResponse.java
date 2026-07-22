package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for product popup/details response used by POS UI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosProductPopupResponse {


    // Product fields
    private String uuid;
    private String title;
    private String sku;
    private String thumbnailUrl;
    private String showcasePrice;
    private String salePrice;
    private Integer availableQuantity;
    private String productType;
    private Boolean isActive;
    private Boolean hasVariants;

    /**
     * Variant galleries - Maps variant UUID to list of gallery images.
     * Example: { "variantUuid": [{ "uuid": "...", "mediaUrl": "...", "sortOrder": 1 }] }
     */
    private Map<String, List<GalleryImage>> galleryImages;

    /**
     * Variant attribute maps - Maps variant UUID to attribute key-value pairs.
     * Example: { "variantUuid": { "Size": "S", "Color": "Black" } }
     */
    private Map<String, Map<String, String>> variantAttributesMap;

    /**
     * Variant attribute list maps - Maps variant UUID to attribute name and list of possible values.
     * Example: { "variantUuid": { "Size": ["S","M"], "Color": ["Black","Red"] } }
     */
    private Map<String, Map<String, List<String>>> variantAttributesListMap;

    /**
     * Variant prices - Maps variant UUID to variant price fields.
     * Example: { "variantUuid": { "price": "1200.00", "showcasePrice": "1500.00", "salePrice": "1200.00" } }
     */
    private Map<String, Map<String, String>> variantPricesMap;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GalleryImage {
        private String uuid;
        private String mediaUrl;
        private Integer sortOrder;
        private String altText;
        private String mimeType;
    }
}
