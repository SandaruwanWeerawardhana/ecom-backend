package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lightweight product search DTO optimized for POS UI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosProductSearchResponse {
    private Long productId;
    private String uuid;
    private String title;
    private String sku;
    private Double showcasePrice;
    private Double salePrice;
    private Integer initialStockQuantity;
    private String thumbnailUrl;
    private Boolean hasVariants;
    private List<PosVariantInfo> variants;
}
