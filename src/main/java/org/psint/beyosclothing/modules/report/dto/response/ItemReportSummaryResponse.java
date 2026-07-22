package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary totals for the item report window.
 * {@code itemValue} is the average revenue per unit sold (totalRevenue / itemsSold).
 * {@code totalRevenue} covers every order regardless of status (plus POS), while
 * {@code completedRevenue} covers only earned sales (DELIVERED/COMPLETED orders plus POS).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemReportSummaryResponse {
    private BigDecimal totalRevenue;
    private BigDecimal completedRevenue;
    private BigDecimal itemsSold;
    private BigDecimal totalOrders;
    private BigDecimal itemValue;
}
