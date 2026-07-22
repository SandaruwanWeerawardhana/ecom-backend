package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight variant info for POS search results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosVariantInfo {
    private Long variantId;
    private String attributeSummary;
    private Double showcasePrice;
    private Integer initialStockQuantity;
}
