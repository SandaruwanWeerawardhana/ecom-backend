package org.psint.beyosclothing.modules.report.service;

import org.psint.beyosclothing.modules.report.dto.response.DailyOrderCountResponse;
import org.psint.beyosclothing.modules.report.dto.response.DashboardOverviewResponse;

import java.util.List;

/**
 * Admin dashboard metrics: headline revenue, order and customer figures.
 */
public interface DashboardService {

    /** Daily revenue, total orders, total customers and monthly revenue in one payload. */
    DashboardOverviewResponse getOverview();

    /** Order volume and revenue for each of the last 7 days (rolling window ending today). */
    List<DailyOrderCountResponse> getDailyOrdersLast7Days();
}
