package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportSummaryResponse {
    private BigDecimal totalRevenue;
    private BigDecimal productsSold;
    private BigDecimal totalOrders;
    private BigDecimal averageOrderValue;
    private BigDecimal expenses;
    private BigDecimal profitNet;
    private BigDecimal lossNet;
    private SaleReportTopSalesResponse topSalesDay;
    private SaleReportTopSalesResponse topSalesWeek;
}
