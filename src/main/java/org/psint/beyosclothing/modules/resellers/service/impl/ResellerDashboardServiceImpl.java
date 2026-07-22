package org.psint.beyosclothing.modules.resellers.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.response.DashboardOverviewResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletFullSummaryResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.service.ResellerDashboardService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of Reseller Dashboard Service
 * Aggregates data from Order, Payment, and Wallet modules via RabbitMQ RPC calls
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerDashboardServiceImpl implements ResellerDashboardService {

    private final ResellerService resellerService;
    private final ResellerWalletService walletService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    private static final long RABBITMQ_TIMEOUT_SECONDS = 5;

    @Override
    public DashboardOverviewResponse getDashboardOverview(Long userId) {
        log.info("=== DASHBOARD OVERVIEW - START ===");
        log.info("Fetching dashboard overview for userId: {}", userId);

        try {
            Reseller reseller = resellerService.getResellerByUserId(userId);
            log.info("Reseller found: {} (ID: {})", reseller.getUuid(), reseller.getId());

            // Fetch wallet balance from local wallet service
            BigDecimal walletBalance = walletService.getWalletSummary(reseller.getId()).getCurrentBalance();
            log.info("Wallet balance retrieved: {}", walletBalance);

            // Fetch dashboard metrics from Order module via RabbitMQ
            Map<String, Object> dashboardData = fetchDashboardMetricsViaRabbitMQ(reseller.getId());

            if (dashboardData == null || !(Boolean) dashboardData.get("success")) {
                log.warn("Failed to fetch dashboard metrics from order module");
                // Return partial response with available data
                return buildPartialDashboardResponse(walletBalance);
            }

            BigDecimal totalSales = getBigDecimalValue(dashboardData.get("totalSales"));
            Long totalOrders = getLongValue(dashboardData.get("totalOrders"));
            Long pendingOrders = getLongValue(dashboardData.get("pendingOrders"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recentOrdersMaps = (List<Map<String, Object>>) dashboardData.get("recentOrders");
            List<DashboardOverviewResponse.RecentOrderSummary> recentOrders = mapRecentOrders(recentOrdersMaps);

            log.info("✅ Dashboard overview retrieved - Total Sales: {}, Orders: {}, Pending: {}",
                    totalSales, totalOrders, pendingOrders);

            DashboardOverviewResponse response = DashboardOverviewResponse.builder()
                    .totalSales(totalSales)
                    .totalOrders(totalOrders)
                    .pendingOrders(pendingOrders)
                    .walletBalance(walletBalance)
                    .recentOrders(recentOrders)
                    .build();

            log.info("=== DASHBOARD OVERVIEW - SUCCESS ===");
            return response;

        } catch (Exception e) {
            log.error("❌ Error fetching dashboard overview: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch dashboard overview: " + e.getMessage(), e);
        }
    }

    @Override
    public WalletFullSummaryResponse getWalletFullSummary(Long userId) {
        log.info("=== WALLET FULL SUMMARY - START ===");
        log.info("Fetching wallet full summary for userId: {}", userId);

        try {
            Reseller reseller = resellerService.getResellerByUserId(userId);
            log.info("Reseller found: {} (ID: {})", reseller.getUuid(), reseller.getId());

            // Fetch wallet balance from local wallet service
            var walletBalance = walletService.getWalletSummary(reseller.getId());
            BigDecimal availableBalance = walletBalance.getCurrentBalance();
            BigDecimal pendingWithdrawals = walletBalance.getPendingWithdrawals();

            log.info("Local wallet data retrieved - Available: {}, Pending: {}",
                    availableBalance, pendingWithdrawals);

            // Fetch payment metrics from Payment module via RabbitMQ
            Map<String, Object> paymentData = fetchWalletSummaryViaRabbitMQ(reseller.getId());

            if (paymentData == null || !(Boolean) paymentData.get("success")) {
                log.warn("Failed to fetch wallet summary from payment module");
                // Return partial response with available data
                return buildPartialWalletResponse(availableBalance, pendingWithdrawals);
            }

            BigDecimal totalEarnings = getBigDecimalValue(paymentData.get("totalEarnings"));
            BigDecimal totalWithdrawn = getBigDecimalValue(paymentData.get("totalWithdrawn"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transactionMaps = (List<Map<String, Object>>) paymentData.get("recentTransactions");
            log.info("Recent transactions data retrieved - Count: {}", transactionMaps != null ? transactionMaps.size() : 0);
            List<WalletFullSummaryResponse.TransactionSummary> recentTransactions = mapTransactions(transactionMaps);

            log.info("✅ Wallet summary retrieved - Total Earnings: {}, Total Withdrawn: {}",
                    totalEarnings, totalWithdrawn);

            WalletFullSummaryResponse response = WalletFullSummaryResponse.builder()
                    .availableBalance(availableBalance)
                    .totalEarnings(totalEarnings)
                    .pendingWithdrawals(pendingWithdrawals)
                    .totalWithdrawn(totalWithdrawn)
                    .recentTransactions(recentTransactions)
                    .build();

            log.info("=== WALLET FULL SUMMARY - SUCCESS ===");
            return response;

        } catch (Exception e) {
            log.error("❌ Error fetching wallet full summary: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch wallet full summary: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch dashboard metrics from Order module via RabbitMQ RPC
     */
    private Map<String, Object> fetchDashboardMetricsViaRabbitMQ(Long resellerId) {
        try {
            log.info("Fetching dashboard metrics from Order module via RabbitMQ - resellerId: {}", resellerId);

            Map<String, Object> request = new HashMap<>();
            request.put("requestType", "GET_RESELLER_DASHBOARD_METRICS");
            request.put("resellerId", resellerId);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(RABBITMQ_TIMEOUT_SECONDS));

            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "reseller.dashboard.metrics.lookup.request",
                    request
            );

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                log.info("Dashboard metrics response received - Success: {}", responseMap.get("success"));
                return responseMap;
            }

            log.error("Invalid response type from RabbitMQ: {}", response != null ? response.getClass() : "null");
            return null;

        } catch (Exception e) {
            log.error("Error fetching dashboard metrics from RabbitMQ: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch wallet summary from Payment module via RabbitMQ RPC
     */
    private Map<String, Object> fetchWalletSummaryViaRabbitMQ(Long resellerId) {
        try {
            log.info("Fetching wallet summary from Payment module via RabbitMQ - resellerId: {}", resellerId);

            Map<String, Object> request = new HashMap<>();
            request.put("requestType", "GET_RESELLER_WALLET_SUMMARY");
            request.put("resellerId", resellerId);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(RABBITMQ_TIMEOUT_SECONDS));

            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange,
                    "reseller.wallet.summary.lookup.request",
                    request
            );

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                log.info("Wallet summary response received - Success: {}", responseMap.get("success"));
                return responseMap;
            }

            log.error("Invalid response type from RabbitMQ: {}", response != null ? response.getClass() : "null");
            return null;

        } catch (Exception e) {
            log.error("Error fetching wallet summary from RabbitMQ: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Map recent orders from RabbitMQ response
     */
    @SuppressWarnings("unchecked")
    private List<DashboardOverviewResponse.RecentOrderSummary> mapRecentOrders(List<Map<String, Object>> orderMaps) {
        List<DashboardOverviewResponse.RecentOrderSummary> orders = new ArrayList<>();

        if (orderMaps == null || orderMaps.isEmpty()) {
            return orders;
        }

        for (Map<String, Object> orderMap : orderMaps) {
            try {
                DashboardOverviewResponse.RecentOrderSummary order = DashboardOverviewResponse.RecentOrderSummary.builder()
                        .orderNumber((String) orderMap.get("orderNumber"))
                        .productName((String) orderMap.get("productName"))
                        .quantity(getIntegerValue(orderMap.get("quantity")))
                        .total(getBigDecimalValue(orderMap.get("total")))
                        .status((String) orderMap.get("status"))
                        .build();
                orders.add(order);
            } catch (Exception e) {
                log.warn("Error mapping order summary: {}", e.getMessage());
            }
        }

        return orders;
    }

    /**
     * Map transactions from RabbitMQ response
     */
    @SuppressWarnings("unchecked")
    private List<WalletFullSummaryResponse.TransactionSummary> mapTransactions(List<Map<String, Object>> transactionMaps) {
        List<WalletFullSummaryResponse.TransactionSummary> transactions = new ArrayList<>();

        if (transactionMaps == null || transactionMaps.isEmpty()) {
            return transactions;
        }

        for (Map<String, Object> txMap : transactionMaps) {
            try {
                WalletFullSummaryResponse.TransactionSummary tx = WalletFullSummaryResponse.TransactionSummary.builder()
                        .type((String) txMap.get("type"))
                        .description((String) txMap.get("description"))
                        .amount(getBigDecimalValue(txMap.get("amount")))
                        .date((String) txMap.get("date"))
                        .status((String) txMap.get("status"))
                        .build();
                transactions.add(tx);
            } catch (Exception e) {
                log.warn("Error mapping transaction: {}", e.getMessage());
            }
        }

        return transactions;
    }

    /**
     * Build partial dashboard response when Order module is unavailable
     */
    private DashboardOverviewResponse buildPartialDashboardResponse(BigDecimal walletBalance) {
        return DashboardOverviewResponse.builder()
                .totalSales(BigDecimal.ZERO)
                .totalOrders(0L)
                .pendingOrders(0L)
                .walletBalance(walletBalance)
                .recentOrders(new ArrayList<>())
                .build();
    }

    /**
     * Build partial wallet response when Payment module is unavailable
     */
    private WalletFullSummaryResponse buildPartialWalletResponse(BigDecimal availableBalance, BigDecimal pendingWithdrawals) {
        return WalletFullSummaryResponse.builder()
                .availableBalance(availableBalance)
                .totalEarnings(BigDecimal.ZERO)
                .pendingWithdrawals(pendingWithdrawals)
                .totalWithdrawn(BigDecimal.ZERO)
                .recentTransactions(new ArrayList<>())
                .build();
    }

    // ========================================
    // Utility Methods for Type Conversion
    // ========================================

    private BigDecimal getBigDecimalValue(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private Long getLongValue(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception e) {
                return 0L;
            }
        }
        return 0L;
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
}

