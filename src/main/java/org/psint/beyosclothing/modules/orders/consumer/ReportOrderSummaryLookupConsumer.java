package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns order-side item report summary values for RabbitMQ RPC callers.
 * Every value counts only orders in the fulfilment pipeline
 * (PROCESSING, COURIER_ORDER_PLACED, OUT_FOR_DELIVERY, DELIVERED, COMPLETED):
 * the order count, order revenue, and the matching order-item quantity and value.
 * {@code totalRevenue} and {@code completedRevenue} are therefore identical.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportOrderSummaryLookupConsumer {

    /**
     * Order statuses that count toward every report summary value. Spans the whole
     * fulfilment pipeline from PROCESSING through to COMPLETED so in-progress orders
     * are reported, not only fully completed ones.
     */
    private static final List<OrderEntity.OrderStatus> REPORTABLE_ORDER_STATUSES = List.of(
            OrderEntity.OrderStatus.PROCESSING,
            OrderEntity.OrderStatus.COURIER_ORDER_PLACED,
            OrderEntity.OrderStatus.OUT_FOR_DELIVERY,
            OrderEntity.OrderStatus.DELIVERED,
            OrderEntity.OrderStatus.COMPLETED
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @RabbitListener(
            queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-order-summary-lookup-request:report.order.summary.lookup.request}", durable = "true"),
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional(value = "orderTransactionManager", readOnly = true)
    public Map<String, Object> handleOrderSummaryLookup(Map<String, Object> request) {
        try {
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end = parseDateTime(request, "end");

            Long orderCount = orderRepository.countOrdersForReport(start, end, REPORTABLE_ORDER_STATUSES);
            long totalOrders = orderCount != null ? orderCount : 0L;
            // source = null: include every source (ONLINE + POS) constrained by status.
            BigDecimal pipelineRevenue = orderRepository.sumOrderRevenueForReport(
                    start, end, REPORTABLE_ORDER_STATUSES, null);
            BigDecimal safeRevenue = pipelineRevenue != null ? pipelineRevenue : BigDecimal.ZERO;
            long itemsSold = orderItemRepository.sumItemQuantityForReport(start, end, REPORTABLE_ORDER_STATUSES);
            BigDecimal itemValue = orderItemRepository.sumItemValueForReport(start, end, REPORTABLE_ORDER_STATUSES);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("totalOrders", totalOrders);
            response.put("totalRevenue", safeRevenue);
            response.put("completedRevenue", safeRevenue);
            response.put("itemsSold", itemsSold);
            response.put("itemValue", itemValue != null ? itemValue : BigDecimal.ZERO);
            return response;
        } catch (Exception e) {
            log.error("Error handling report order summary lookup", e);
            return buildErrorResponse(request, "Error fetching order summary: " + e.getMessage());
        }
    }

    private LocalDateTime parseDateTime(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return LocalDateTime.parse(request.get(key).toString());
    }

    private Map<String, Object> buildErrorResponse(Map<String, Object> request, String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("requestId", request != null ? request.get("requestId") : null);
        response.put("error", error);
        return response;
    }
}
