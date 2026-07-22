package org.psint.beyosclothing.modules.pos.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns POS-side item report summary values for RabbitMQ RPC callers.
 * All values cover completed POS carts (checked out: not draft, not active)
 * created within the window. POS revenue is the cart subtotal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportPosSummaryLookupConsumer {

    private final PosCartRepository posCartRepository;
    private final PosCartItemRepository posCartItemRepository;

    @RabbitListener(queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-pos-summary-lookup-request:report.pos.summary.lookup.request}", durable = "true"))
    @Transactional(value = "posTransactionManager", readOnly = true)
    public Map<String, Object> handlePosSummaryLookup(Map<String, Object> request) {
        try {
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end = parseDateTime(request, "end");

            BigDecimal totalRevenue = posCartRepository.sumCartSubtotalForReport(start, end);
            long totalOrders = posCartRepository.countCartsForReport(start, end);
            long itemsSold = posCartItemRepository.sumCartItemQuantityForReport(start, end);
            BigDecimal itemValue = posCartItemRepository.sumCartItemValueForReport(start, end);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
            response.put("totalOrders", totalOrders);
            response.put("itemsSold", itemsSold);
            response.put("itemValue", itemValue != null ? itemValue : BigDecimal.ZERO);
            return response;
        } catch (Exception e) {
            log.error("Error handling report POS summary lookup", e);
            return buildErrorResponse(request, "Error fetching POS summary: " + e.getMessage());
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

