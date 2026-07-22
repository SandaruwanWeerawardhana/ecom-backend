package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload for the variable-product detail view (variation breakdown).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableProductDetailResponse {
    private Long productId;
    private String productName;
    private String type;            // always VARIABLE
    private List<VariationDetailResponse> variations;
}
