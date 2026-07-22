package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for Dashboard Overview Response
 * Combines metrics from reseller's wallet and orders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reseller dashboard overview with key metrics and recent orders")
public class DashboardOverviewResponse {

    @Schema(description = "Total sales (sum of all completed orders)", example = "12450.00")
    private BigDecimal totalSales;

    @Schema(description = "Total number of orders", example = "45")
    private Long totalOrders;

    @Schema(description = "Number of pending orders (PENDING, PROCESSING, OUT_FOR_DELIVERY)", example = "8")
    private Long pendingOrders;

    @Schema(description = "Current wallet balance", example = "3240.00")
    private BigDecimal walletBalance;

    @Schema(description = "List of recent orders (up to 5 most recent)")
    private List<RecentOrderSummary> recentOrders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary of a recent order")
    public static class RecentOrderSummary {

        @Schema(description = "Order ID/Number", example = "ORD-001")
        private String orderNumber;

        @Schema(description = "Product name", example = "T-Shirt Blue")
        private String productName;

        @Schema(description = "Quantity ordered", example = "5")
        private Integer quantity;

        @Schema(description = "Order total amount", example = "125.00")
        private BigDecimal total;

        @Schema(description = "Order status", example = "Pending")
        private String status;
    }
}

