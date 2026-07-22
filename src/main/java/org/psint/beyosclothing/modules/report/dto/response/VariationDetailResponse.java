package org.psint.beyosclothing.modules.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One variation row in the variable-product detail view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VariationDetailResponse {
    private Long variationId;
    private String sku;
    private String color;
    private String size;
    private String unitPrice;
    private Integer stock;
    private long sold;
    private String totalRevenue;
}
