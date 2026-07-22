package org.psint.beyosclothing.modules.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.report.dto.response.DailyOrderCountResponse;
import org.psint.beyosclothing.modules.report.dto.response.DashboardOverviewResponse;
import org.psint.beyosclothing.modules.report.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin dashboard endpoints for the dashboard overview page.
 */
@RestController
@RequestMapping("/api/v1/admin/reports/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Dashboard", description = "Admin dashboard overview APIs")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Dashboard overview",
            description = "Daily revenue, total orders, total customers and monthly revenue")
    public ResponseEntity<APIResponse<DashboardOverviewResponse>> getOverview() {
        DashboardOverviewResponse data = dashboardService.getOverview();
        return ResponseEntity.ok(APIResponse.success("Dashboard overview fetched successfully", data));
    }

    @GetMapping("/daily-orders-last-7-days")
    @Operation(summary = "Daily orders and revenue for the last 7 days",
            description = "Order volume and revenue for each of the last 7 days (rolling window ending today)")
    public ResponseEntity<APIResponse<List<DailyOrderCountResponse>>> getDailyOrdersLast7Days() {
        List<DailyOrderCountResponse> data = dashboardService.getDailyOrdersLast7Days();
        return ResponseEntity.ok(APIResponse.success("Daily orders for the last 7 days fetched successfully", data));
    }
}
