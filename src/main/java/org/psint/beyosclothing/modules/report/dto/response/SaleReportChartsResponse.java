package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportChartsResponse {
    private List<SaleReportTrendResponse> revenueTrend;
    private List<SaleReportCategoryResponse> categoryRevenue;
    private List<SaleReportProductRevenueResponse> productRevenue;
}
