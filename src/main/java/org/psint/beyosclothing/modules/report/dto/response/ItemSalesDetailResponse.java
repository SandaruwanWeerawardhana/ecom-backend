package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One row of the Sales Details table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSalesDetailResponse {
    private String date;
    private String time;
    private Long productId;
    private String productName;
    private String category;
    private String image;
    private String type;            // SIMPLE | VARIABLE
    private String unitPrice;
    private Long unitsSold;
    private String totalRevenue;
    private List<VariationDetailResponse> variableDetails;
}
