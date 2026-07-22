package org.psint.beyosclothing.modules.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Top metric cards for the admin dashboard: today's earned revenue, all-time
 * order count, registered customer count and the current month's earned revenue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewResponse {
    private BigDecimal dailyRevenue;
    private long totalOrders;
    private long totalCustomers;
    private BigDecimal monthlyRevenue;
}
