package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Consumer for Reseller Dashboard Metrics Lookup
 * Handles RPC requests from Reseller module to fetch dashboard data
 *
 * Request: { "requestType": "GET_RESELLER_DASHBOARD_METRICS", "resellerId": Long }
 * Response: { "success": boolean, "totalSales": BigDecimal, "totalOrders": Long,
 *             "pendingOrders": Long, "recentOrders": List<Map> }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerDashboardMetricsConsumer {

    private final OrderRepository orderRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.reseller-dashboard-metrics-lookup-request:reseller.dashboard.metrics.lookup.request}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public Map<String, Object> handleDashboardMetricsLookup(Map<String, Object> request) {
        log.info("=== RESELLER DASHBOARD METRICS LOOKUP CONSUMER TRIGGERED ===");

        try {
            Long resellerId = null;
            Object resellerIdObj = request.get("resellerId");
            if (resellerIdObj instanceof Number) {
                resellerId = ((Number) resellerIdObj).longValue();
            }

            if (resellerId == null) {
                log.warn("⚠️ Missing resellerId in dashboard metrics lookup request");
                return buildErrorResponse("Missing resellerId in request");
            }

            log.info("Fetching dashboard metrics for resellerId: {}", resellerId);

            // Calculate total sales (sum of all completed orders for this reseller)
            BigDecimal totalSales = orderRepository.getTotalSalesForReseller(resellerId);
            if (totalSales == null) {
                totalSales = BigDecimal.ZERO;
            }

            // Get total orders count
            Long totalOrders = orderRepository.countByResellerId(resellerId);
            if (totalOrders == null) {
                totalOrders = 0L;
            }

            // Get pending orders count (PENDING, PROCESSING, OUT_FOR_DELIVERY)
            Long pendingOrders = orderRepository.countPendingOrdersByResellerId(resellerId);
            if (pendingOrders == null) {
                pendingOrders = 0L;
            }

            // Get recent orders (latest 5)
            var recentOrderEntities = orderRepository.findRecentOrdersByResellerId(resellerId, 5);
            List<Map<String, Object>> recentOrders = mapRecentOrders(recentOrderEntities);

            log.info("✅ Dashboard metrics retrieved - Total Sales: {}, Orders: {}, Pending: {}",
                    totalSales, totalOrders, pendingOrders);

            return buildSuccessResponse(totalSales, totalOrders, pendingOrders, recentOrders);

        } catch (Exception e) {
            log.error("❌ Error in reseller dashboard metrics lookup consumer: {}", e.getMessage(), e);
            return buildErrorResponse("Error fetching dashboard metrics: " + e.getMessage());
        }
    }

    /**
     * Map order entities to order summaries for dashboard
     */
    private List<Map<String, Object>> mapRecentOrders(List<?> orderEntities) {
        List<Map<String, Object>> orders = new ArrayList<>();

        if (orderEntities == null || orderEntities.isEmpty()) {
            return orders;
        }

        for (Object entity : orderEntities) {
            try {
                // Using reflection to avoid direct cross-module entity dependency
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("orderNumber", getFieldValue(entity, "orderNumber"));
                orderMap.put("productName", getFieldValue(entity, "productName"));
                orderMap.put("quantity", getFieldValue(entity, "quantity"));
                orderMap.put("total", getFieldValue(entity, "total"));
                orderMap.put("status", getFieldValue(entity, "status"));
                orders.add(orderMap);
            } catch (Exception e) {
                log.warn("Error mapping order summary: {}", e.getMessage());
            }
        }

        return orders;
    }

    /**
     * Get field value using reflection (avoids direct entity access)
     */
    private Object getFieldValue(Object entity, String fieldName) {
        try {
            var field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (Exception e) {
            log.debug("Could not retrieve field {}: {}", fieldName, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildSuccessResponse(BigDecimal totalSales, Long totalOrders,
                                                      Long pendingOrders, List<Map<String, Object>> recentOrders) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalSales", totalSales);
        response.put("totalOrders", totalOrders);
        response.put("pendingOrders", pendingOrders);
        response.put("recentOrders", recentOrders);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }
}

