package org.psint.beyosclothing.modules.resellers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.DashboardOverviewResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletFullSummaryResponse;
import org.psint.beyosclothing.modules.resellers.service.ResellerDashboardService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Reseller Dashboard Operations
 * Provides aggregated metrics and overview data
 */
@RestController
@RequestMapping("/api/v1/resellers/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Dashboard", description = "Dashboard metrics and overview for resellers")
public class ResellerDashboardController {

    private final ResellerDashboardService dashboardService;
    private final JwtUtil jwtUtil;

    /**
     * Get dashboard overview with key metrics
     * Displays: Total Sales, My Orders, Pending Orders, Wallet Balance, Recent Orders
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(
            summary = "Get dashboard overview",
            description = "Returns dashboard overview with key metrics including total sales, order counts, wallet balance, and recent orders. " +
                    "Aggregates data from Order and Wallet modules."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dashboard overview retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DashboardOverviewResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - RESELLER role required"),
            @ApiResponse(responseCode = "500", description = "Service unavailable - order service timeout or database error")
    })
    public ResponseEntity<APIResponse<DashboardOverviewResponse>> getDashboardOverview(
            Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);

        log.info("Reseller GET /api/v1/resellers/dashboard/overview - userId: {}", userId);

        try {
            DashboardOverviewResponse response = dashboardService.getDashboardOverview(userId);
            log.info("Dashboard overview retrieved successfully for userId: {}", userId);
            return ResponseEntity.ok(APIResponse.success("Dashboard overview retrieved successfully", response));
        } catch (Exception e) {
            log.error("Error fetching dashboard overview for userId: {}", userId, e);
            throw new org.psint.beyosclothing.core.exception.ServiceException(
                    "Failed to fetch dashboard overview. Please try again later.");
        }
    }

    /**
     * Get wallet full summary with transactions
     * Displays: Available Balance, Total Earnings, Pending Withdrawals, Total Withdrawn, Recent Transactions
     */
    @GetMapping("/wallet/full-summary")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(
            summary = "Get wallet full summary",
            description = "Returns comprehensive wallet summary including available balance, lifetime earnings, withdrawals, and recent transactions. " +
                    "Aggregates data from Wallet and Payment modules."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Wallet summary retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WalletFullSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - token missing or expired"),
            @ApiResponse(responseCode = "403", description = "Forbidden - RESELLER role required"),
            @ApiResponse(responseCode = "500", description = "Service unavailable - payment service timeout or database error")
    })
    public ResponseEntity<APIResponse<WalletFullSummaryResponse>> getWalletFullSummary(
            Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);

        log.info("Reseller GET /api/v1/resellers/dashboard/wallet/full-summary - userId: {}", userId);

        try {
            WalletFullSummaryResponse response = dashboardService.getWalletFullSummary(userId);
            log.info("Wallet full summary retrieved successfully for userId: {}", userId);
            return ResponseEntity.ok(APIResponse.success("Wallet summary retrieved successfully", response));
        } catch (Exception e) {
            log.error("Error fetching wallet full summary for userId: {}", userId, e);
            throw new org.psint.beyosclothing.core.exception.ServiceException(
                    "Failed to fetch wallet summary. Please try again later.");
        }
    }
}

