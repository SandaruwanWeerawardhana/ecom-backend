package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.response.DashboardOverviewResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletFullSummaryResponse;

/**
 * Service interface for Reseller Dashboard Operations
 * Aggregates data from multiple modules via RabbitMQ
 */
public interface ResellerDashboardService {

    /**
     * Get dashboard overview with key metrics
     * Fetches: total sales, order counts, wallet balance, recent orders
     *
     * @param userId User ID of the reseller
     * @return Dashboard overview response
     */
    DashboardOverviewResponse getDashboardOverview(Long userId);

    /**
     * Get wallet full summary with transactions
     * Fetches: available balance, total earnings, pending withdrawals, total withdrawn, recent transactions
     *
     * @param userId User ID of the reseller
     * @return Wallet full summary response
     */
    WalletFullSummaryResponse getWalletFullSummary(Long userId);
}

