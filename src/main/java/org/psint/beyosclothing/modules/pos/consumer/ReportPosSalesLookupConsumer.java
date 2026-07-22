package org.psint.beyosclothing.modules.pos.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
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
 * Owns POS-side item report sales-detail aggregates for RabbitMQ RPC callers.
 * Mirrors {@code ReportOrderSalesLookupConsumer} so the report can merge order and POS rows.
 * <p>
 * All values cover completed POS carts (checked out: not draft, not active) created
 * within the window. POS cart items carry no product/variant title, so those fields are
 * returned as {@code null} and resolved by the caller via the product lookup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportPosSalesLookupConsumer {

    private final PosCartItemRepository posCartItemRepository;

    @RabbitListener(queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-pos-sales-lookup-request:report.pos.sales.lookup.request}", durable = "true"))
    @Transactional(value = "posTransactionManager", readOnly = true)
    public Map<String, Object> handlePosSalesLookup(Map<String, Object> request) {
        try {
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end = parseDateTime(request, "end");

            List<Map<String, Object>> rows = posCartItemRepository
                    .findProductSalesForReport(start, end)
                    .stream()
                    .map(this::mapProductRow)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("rows", rows);
            return response;
        } catch (Exception e) {
            log.error("Error handling report POS sales lookup", e);
            return buildErrorResponse(request, "Error fetching POS sales details: " + e.getMessage());
        }
    }

    @RabbitListener(queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-pos-variation-sales-lookup-request:report.pos.variation.sales.lookup.request}", durable = "true"))
    @Transactional(value = "posTransactionManager", readOnly = true)
    public Map<String, Object> handlePosVariationSalesLookup(Map<String, Object> request) {
        try {
            Long productId = toLong(request.get("productId"));
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end = parseDateTime(request, "end");

            List<Map<String, Object>> rows = posCartItemRepository
                    .findVariationSalesForReport(productId, start, end)
                    .stream()
                    .map(this::mapVariationRow)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("rows", rows);
            return response;
        } catch (Exception e) {
            log.error("Error handling report POS variation sales lookup", e);
            return buildErrorResponse(request, "Error fetching POS variation sales details: " + e.getMessage());
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
        result.put("productTitle", null);
        return result;
    }

    private Map<String, Object> mapVariationRow(Object[] row) {
        Map<String, Object> result = new HashMap<>();
        result.put("variantId", toLong(row[0]));
        result.put("sold", toLong(row[1]));
        result.put("totalRevenue", toBigDecimal(row[2]));
        result.put("minUnitPrice", toBigDecimal(row[3]));
        result.put("maxUnitPrice", toBigDecimal(row[4]));
        result.put("variantTitle", null);
        return result;
    }

    private LocalDateTime parseDateTime(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return LocalDateTime.parse(request.get(key).toString());
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
