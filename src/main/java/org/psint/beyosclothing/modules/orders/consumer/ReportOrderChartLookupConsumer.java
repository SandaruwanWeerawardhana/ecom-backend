package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides order-revenue chart data for the report module via RabbitMQ RPC.
 * <p>
 * Request keys: {@code requestId}, {@code start} (ISO datetime), {@code end} (ISO datetime),
 * {@code singleDay} (boolean string — "true" for hourly buckets, else daily).
 * <p>
 * Response keys: {@code success}, {@code requestId}, {@code points} (List of point maps).
 * Each hourly point map: {@code hour} (int), {@code itemsSold} (long), {@code revenue} (BigDecimal).
 * Each daily  point map: {@code year}, {@code month}, {@code day} (int), {@code itemsSold} (long), {@code revenue} (BigDecimal).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportOrderChartLookupConsumer {

    private final OrderItemRepository orderItemRepository;

    @RabbitListener(
            queuesToDeclare = @Queue(value = "${app.rabbitmq.queue.report-order-chart-lookup-request:report.order.chart.lookup.request}", durable = "true"),
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional(value = "orderTransactionManager", readOnly = true)
    public Map<String, Object> handleOrderChartLookup(Map<String, Object> request) {
        try {
            LocalDateTime start = parseDateTime(request, "start");
            LocalDateTime end   = parseDateTime(request, "end");
            boolean singleDay   = Boolean.parseBoolean(String.valueOf(request.getOrDefault("singleDay", "false")));

            List<Map<String, Object>> points = singleDay
                    ? buildHourlyPoints(start, end)
                    : buildDailyPoints(start, end);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("points", points);
            return response;
        } catch (Exception e) {
            log.error("Error handling report order chart lookup", e);
            return buildErrorResponse(request, "Error fetching order chart: " + e.getMessage());
        }
    }

    /** O(n) over matching order items — aggregation done in-DB. */
    private List<Map<String, Object>> buildHourlyPoints(LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = orderItemRepository.aggregateHourlyItemsSold(start, end);
        List<Map<String, Object>> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> point = new HashMap<>();
            point.put("hour",      toInt(row[0]));
            point.put("itemsSold", toLong(row[1]));
            point.put("revenue",   toBigDecimal(row[2]));
            points.add(point);
        }
        return points;
    }

    /** O(n) over matching order items — aggregation done in-DB. */
    private List<Map<String, Object>> buildDailyPoints(LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = orderItemRepository.aggregateDailyItemsSold(start, end);
        List<Map<String, Object>> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> point = new HashMap<>();
            point.put("year",      toInt(row[0]));
            point.put("month",     toInt(row[1]));
            point.put("day",       toInt(row[2]));
            point.put("itemsSold", toLong(row[3]));
            point.put("revenue",   toBigDecimal(row[4]));
            points.add(point);
        }
        return points;
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
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
