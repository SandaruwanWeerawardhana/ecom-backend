package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
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
 * Owns order-side item report sales-detail aggregates for RabbitMQ RPC callers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportOrderSalesLookupConsumer {

    /**
     * Order statuses that count as a confirmed sale in the item report. Covers the whole
     * fulfilment pipeline from PROCESSING through to COMPLETED so in-progress orders are
     * reported, not only fully completed ones.
     */
    private static final List<OrderEntity.OrderStatus> SALE_COUNTING_STATUSES = List.of(
            OrderEntity.OrderStatus.PROCESSING,
            OrderEntity.OrderStatus.COURIER_ORDER_PLACED,
            OrderEntity.OrderStatus.OUT_FOR_DELIVERY,
            OrderEntity.OrderStatus.DELIVERED,
            OrderEntity.OrderStatus.COMPLETED
    );

    private final OrderItemRepository orderItemRepository;

    @RabbitListener(
            queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-order-sales-lookup-request:report.order.sales.lookup.request}", durable = "true"),
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional(value = "orderTransactionManager", readOnly = true)
    public Map<String, Object> handleOrderSalesLookup(Map<String, Object> request) {
        try {
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end = parseDateTime(request, "end");
            String search = normalizeString(request.get("search"));

            List<Map<String, Object>> rows = orderItemRepository
                    .findProductSalesForReport(start, end, SALE_COUNTING_STATUSES, search)
                    .stream()
                    .map(this::mapProductRow)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("rows", rows);
            return response;
        } catch (Exception e) {
            log.error("Error handling report order sales lookup", e);
            return buildErrorResponse(request, "Error fetching order sales details: " + e.getMessage());
        }
    }

    @RabbitListener(
            queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-order-variation-sales-lookup-request:report.order.variation.sales.lookup.request}", durable = "true"),
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional(value = "orderTransactionManager", readOnly = true)
    public Map<String, Object> handleOrderVariationSalesLookup(Map<String, Object> request) {
        try {
            Long productId = toLong(request.get("productId"));
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end = parseDateTime(request, "end");

            List<Map<String, Object>> rows = orderItemRepository
                    .findVariationSalesForReport(productId, start, end, SALE_COUNTING_STATUSES)
                    .stream()
                    .map(this::mapVariationRow)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("rows", rows);
            return response;
        } catch (Exception e) {
            log.error("Error handling report order variation sales lookup", e);
            return buildErrorResponse(request, "Error fetching order variation sales details: " + e.getMessage());
        }
    }

    private Map<String, Object> mapProductRow(Object[] row) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime latestSaleAt = (LocalDateTime) row[1];
        result.put("productId", toLong(row[0]));
        result.put("date", latestSaleAt != null ? latestSaleAt.toLocalDate().toString() : null);
        result.put("time", latestSaleAt != null ? latestSaleAt.toLocalTime().withSecond(0).withNano(0).toString() : null);
        result.put("unitsSold", toLong(row[2]));
        result.put("totalRevenue", toBigDecimal(row[3]));
        result.put("minUnitPrice", toBigDecimal(row[4]));
        result.put("maxUnitPrice", toBigDecimal(row[5]));
        result.put("productTitle", row[6] != null ? row[6].toString() : null);
        return result;
    }

    private Map<String, Object> mapVariationRow(Object[] row) {
        Map<String, Object> result = new HashMap<>();
        result.put("variantId", toLong(row[0]));
        result.put("sold", toLong(row[1]));
        result.put("totalRevenue", toBigDecimal(row[2]));
        result.put("minUnitPrice", toBigDecimal(row[3]));
        result.put("maxUnitPrice", toBigDecimal(row[4]));
        result.put("variantTitle", row[5] != null ? row[5].toString() : null);
        return result;
    }

    private LocalDateTime parseDateTime(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return LocalDateTime.parse(request.get(key).toString());
    }

    private String normalizeString(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().trim();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private Map<String, Object> buildErrorResponse(Map<String, Object> request, String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("requestId", request != null ? request.get("requestId") : null);
        response.put("error", error);
        return response;
    }
}
