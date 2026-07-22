package org.psint.beyosclothing.modules.report.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckRequest;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckResponse;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductBulkLookupResponse;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupRequest;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupResponse;
import org.psint.beyosclothing.modules.report.dto.external.ProductLookupInfo;
import org.psint.beyosclothing.modules.report.dto.external.VariantLookupInfo;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RabbitMQ request-reply client for the report module's cross-module reads.
 * <p>
 * This is intentionally a plain concrete {@code @Service} (no interface) so the
 * report module talks to other modules purely through the shared transport
 * DTOs. It reuses the existing Product/Inventory lookup contracts:
 * <ul>
 *   <li>{@code inventory.product.bulk.lookup.request} (product exchange) for
 *       product/variant title, type, SKU and attribute summary;</li>
 *   <li>{@code inventory.stock.check} (inventory exchange) for stock quantity.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportLookupServiceImpl {

    private static final String PRODUCT_BULK_LOOKUP_ROUTING_KEY = "inventory.product.bulk.lookup.request";
    private static final String STOCK_CHECK_ROUTING_KEY = "inventory.stock.check";

    // The default exchange ("") routes directly to a named queue.
    private static final String DEFAULT_EXCHANGE = "";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.queue.report-order-summary-lookup-request:report.order.summary.lookup.request}")
    private String orderSummaryQueue;

    @Value("${app.rabbitmq.queue.report-pos-summary-lookup-request:report.pos.summary.lookup.request}")
    private String posSummaryQueue;

    @Value("${app.rabbitmq.queue.report-order-chart-lookup-request:report.order.chart.lookup.request}")
    private String orderChartQueue;

    @Value("${app.rabbitmq.queue.report-order-sales-lookup-request:report.order.sales.lookup.request}")
    private String reportOrderSalesQueue;

    @Value("${app.rabbitmq.queue.report-order-variation-sales-lookup-request:report.order.variation.sales.lookup.request}")
    private String reportOrderVariationSalesQueue;

    @Value("${app.rabbitmq.queue.report-pos-chart-lookup-request:report.pos.chart.lookup.request}")
    private String posChartQueue;

    @Value("${app.rabbitmq.queue.report-pos-sales-lookup-request:report.pos.sales.lookup.request}")
    private String reportPosSalesQueue;

    @Value("${app.rabbitmq.queue.report-pos-variation-sales-lookup-request:report.pos.variation.sales.lookup.request}")
    private String reportPosVariationSalesQueue;

    /**
     * Aggregated order-side summary values fetched from the order module.
     * {@code totalRevenue} is the total of every order regardless of status, while
     * {@code completedRevenue} is only the total of DELIVERED/COMPLETED orders.
     */
    public record OrderSummaryResult(
            BigDecimal totalRevenue, BigDecimal completedRevenue, long totalOrders, long itemsSold, BigDecimal itemValue) {}

    /** Aggregated POS-side summary values fetched from the POS module. */
    public record PosSummaryResult(BigDecimal totalRevenue, long totalOrders, long itemsSold, BigDecimal itemValue) {}

    /** One point on the chart, returned from the order module. */
    public record ChartPoint(String date, String label, BigDecimal revenue, long itemsSold) {}

    /** Product-level sales aggregate returned from the order module. */
    public record ProductSalesResult(
            Long productId,
            String date,
            String time,
            long unitsSold,
            BigDecimal totalRevenue,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice,
            String productTitle
    ) {}

    /** Variant-level sales aggregate returned from the order module. */
    public record VariationSalesResult(
            Long variantId,
            long sold,
            BigDecimal totalRevenue,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice,
            String variantTitle
    ) {}

    /**
     * Fetch totalRevenue (every order regardless of status), completedRevenue
     * (DELIVERED/COMPLETED orders only), totalOrders, itemsSold and itemValue
     * from the order module for the given window.
     * Sends to {@code report.order.summary.lookup.request} (consumed by ReportOrderSummaryLookupConsumer).
     * Returns zero-value result on any failure so the summary endpoint degrades gracefully.
     * Time complexity: O(1) — single RabbitMQ round-trip.
     */
    public OrderSummaryResult lookupOrderSummary(LocalDateTime start, LocalDateTime end) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("start", start.toString());
            request.put("end", end.toString());

            Object rawResponse = rabbitTemplate.convertSendAndReceive(DEFAULT_EXCHANGE, orderSummaryQueue, request);
            if (rawResponse == null) {
                log.warn("No response from order summary lookup queue");
                return emptyOrderSummary();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.convertValue(rawResponse, Map.class);
            if (!Boolean.TRUE.equals(response.get("success"))) {
                log.warn("Order summary lookup returned failure: {}", response.get("error"));
                return emptyOrderSummary();
            }

            return new OrderSummaryResult(
                    bigDecimalOrZero(response.get("totalRevenue")),
                    bigDecimalOrZero(response.get("completedRevenue")),
                    longOrZero(response.get("totalOrders")),
                    longOrZero(response.get("itemsSold")),
                    bigDecimalOrZero(response.get("itemValue")));
        } catch (Exception e) {
            log.error("Order summary lookup failed for window {} - {}", start, end, e);
            return emptyOrderSummary();
        }
    }

    private OrderSummaryResult emptyOrderSummary() {
        return new OrderSummaryResult(BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO);
    }

    /**
     * Fetch totalRevenue, totalOrders, itemsSold and itemValue from the POS module
     * for the given window (completed POS carts only). POS revenue is the cart subtotal.
     * Returns zero-value result on any failure so the summary endpoint degrades gracefully.
     * Time complexity: O(1) — single RabbitMQ round-trip.
     */
    public PosSummaryResult lookupPosSummary(LocalDateTime start, LocalDateTime end) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("start", start.toString());
            request.put("end", end.toString());

            Object rawResponse = rabbitTemplate.convertSendAndReceive(DEFAULT_EXCHANGE, posSummaryQueue, request);
            if (rawResponse == null) {
                log.warn("No response from POS summary lookup queue");
                return new PosSummaryResult(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.convertValue(rawResponse, Map.class);
            if (!Boolean.TRUE.equals(response.get("success"))) {
                log.warn("POS summary lookup returned failure: {}", response.get("error"));
                return new PosSummaryResult(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO);
            }

            return new PosSummaryResult(
                    bigDecimalOrZero(response.get("totalRevenue")),
                    longOrZero(response.get("totalOrders")),
                    longOrZero(response.get("itemsSold")),
                    bigDecimalOrZero(response.get("itemValue")));
        } catch (Exception e) {
            log.error("POS summary lookup failed for window {} - {}", start, end, e);
            return new PosSummaryResult(BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO);
        }
    }

    private long longOrZero(Object value) {
        Long parsed = objectMapper.convertValue(value, Long.class);
        return parsed != null ? parsed : 0L;
    }

    private BigDecimal bigDecimalOrZero(Object value) {
        BigDecimal parsed = objectMapper.convertValue(value, BigDecimal.class);
        return parsed != null ? parsed : BigDecimal.ZERO;
    }

    /**
     * Fetch revenue chart points from the order module for the given window.
     * Sends to {@code report.order.chart.lookup.request} (consumed by ReportOrderChartLookupConsumer).
     * {@code singleDay=true} returns hourly buckets; false returns daily buckets.
     * Labels are formatted as "HH:00" for hourly and "EEE" (Mon/Tue…) for daily.
     * Returns an empty list on any failure so the chart endpoint degrades gracefully.
     * Time complexity: O(b) over returned buckets (one RabbitMQ round-trip).
     */
    public List<ChartPoint> lookupOrderChart(LocalDateTime start, LocalDateTime end, boolean singleDay) {
        return lookupChart(orderChartQueue, "order", start, end, singleDay);
    }

    /**
     * Fetch revenue chart points from the POS module for the given window.
     * Sends to {@code report.pos.chart.lookup.request} (consumed by ReportPosChartLookupConsumer).
     * Buckets and labels match {@link #lookupOrderChart} so the two series can be merged by date.
     * Returns an empty list on any failure so the chart endpoint degrades gracefully.
     */
    public List<ChartPoint> lookupPosChart(LocalDateTime start, LocalDateTime end, boolean singleDay) {
        return lookupChart(posChartQueue, "POS", start, end, singleDay);
    }

    /**
     * Shared chart round-trip used by both the order and POS chart lookups. The two
     * sources return identical point shapes, so only the queue and log label differ.
     */
    @SuppressWarnings("unchecked")
    private List<ChartPoint> lookupChart(String queue, String source,
                                         LocalDateTime start, LocalDateTime end, boolean singleDay) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("start", start.toString());
            request.put("end", end.toString());
            request.put("singleDay", String.valueOf(singleDay));

            Object rawResponse = rabbitTemplate.convertSendAndReceive(DEFAULT_EXCHANGE, queue, request);
            if (rawResponse == null) {
                log.warn("No response from {} chart lookup queue", source);
                return Collections.emptyList();
            }

            Map<String, Object> response = objectMapper.convertValue(rawResponse, Map.class);
            if (!Boolean.TRUE.equals(response.get("success"))) {
                log.warn("{} chart lookup returned failure: {}", source, response.get("error"));
                return Collections.emptyList();
            }

            List<Map<String, Object>> points = objectMapper.convertValue(response.get("points"), List.class);
            if (points == null) {
                return Collections.emptyList();
            }

            return singleDay
                    ? buildHourlyChartPoints(points, start)
                    : buildDailyChartPoints(points, start, end);
        } catch (Exception e) {
            log.error("{} chart lookup failed for window {} - {}", source, start, end, e);
            return Collections.emptyList();
        }
    }


    /**
     * Builds a complete 24-hour series from raw consumer points. Hours with no sales are
     * filled with zero revenue and zero itemsSold so the chart always renders a full day.
     */
    @SuppressWarnings("unchecked")
    private List<ChartPoint> buildHourlyChartPoints(List<Map<String, Object>> points, LocalDateTime start) {
        Map<Integer, ChartPoint> byHour = points.stream()
                .map(p -> {
                    int hour = objectMapper.convertValue(p.get("hour"), Integer.class);
                    BigDecimal revenue = objectMapper.convertValue(p.get("revenue"), BigDecimal.class);
                    Long itemsSold = objectMapper.convertValue(p.get("itemsSold"), Long.class);
                    String dateStr = start.toLocalDate().toString();
                    String label = String.format("%02d:00", hour);
                    return new ChartPoint(dateStr, label,
                            revenue != null ? revenue : BigDecimal.ZERO,
                            itemsSold != null ? itemsSold : 0L);
                })
                .collect(Collectors.toMap(p -> Integer.parseInt(p.label().split(":")[0]), Function.identity()));

        String dateStr = start.toLocalDate().toString();
        List<ChartPoint> result = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d:00", h);
            result.add(byHour.getOrDefault(h, new ChartPoint(dateStr, label, BigDecimal.ZERO, 0L)));
        }
        return result;
    }

    /**
     * Builds a complete day-by-day series from raw consumer points. Days with no sales are
     * filled with zero revenue and zero itemsSold so the chart always renders the full range.
     */
    @SuppressWarnings("unchecked")
    private List<ChartPoint> buildDailyChartPoints(List<Map<String, Object>> points, LocalDateTime start, LocalDateTime end) {
        Map<String, ChartPoint> byDate = points.stream()
                .map(p -> {
                    int year  = objectMapper.convertValue(p.get("year"),  Integer.class);
                    int month = objectMapper.convertValue(p.get("month"), Integer.class);
                    int day   = objectMapper.convertValue(p.get("day"),   Integer.class);
                    BigDecimal revenue = objectMapper.convertValue(p.get("revenue"), BigDecimal.class);
                    Long itemsSold = objectMapper.convertValue(p.get("itemsSold"), Long.class);
                    LocalDate date = LocalDate.of(year, month, day);
                    String label = date.getDayOfWeek().name().charAt(0)
                            + date.getDayOfWeek().name().substring(1, 3).toLowerCase();
                    return new ChartPoint(date.toString(), label,
                            revenue != null ? revenue : BigDecimal.ZERO,
                            itemsSold != null ? itemsSold : 0L);
                })
                .collect(Collectors.toMap(ChartPoint::date, Function.identity()));

        List<ChartPoint> result = new ArrayList<>();
        LocalDate current = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        while (!current.isAfter(endDate)) {
            String dateStr = current.toString();
            String label = current.getDayOfWeek().name().charAt(0)
                    + current.getDayOfWeek().name().substring(1, 3).toLowerCase();
            result.add(byDate.getOrDefault(dateStr, new ChartPoint(dateStr, label, BigDecimal.ZERO, 0L)));
            current = current.plusDays(1);
        }
        return result;
    }

    /**
     * Fetch product-level sales aggregates from the order module for the given window.
     */
    public List<ProductSalesResult> lookupProductSales(LocalDateTime start, LocalDateTime end, String search) {
        try {
            Map<String, Object> request = baseWindowRequest(start, end);
            request.put("search", search);
            return sendRowsRequest(reportOrderSalesQueue, "order sales", request).stream()
                    .map(this::toProductSalesResult)
                    .toList();
        } catch (Exception e) {
            log.error("Order sales lookup failed for window {} - {}", start, end, e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch product-level sales aggregates from the POS module (completed carts) for the window.
     * POS cart items carry no title, so name search is applied caller-side after the product lookup.
     */
    public List<ProductSalesResult> lookupPosProductSales(LocalDateTime start, LocalDateTime end) {
        try {
            return sendRowsRequest(reportPosSalesQueue, "POS sales", baseWindowRequest(start, end)).stream()
                    .map(this::toProductSalesResult)
                    .toList();
        } catch (Exception e) {
            log.error("POS sales lookup failed for window {} - {}", start, end, e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch variant-level sales aggregates from the order module for one product.
     */
    public List<VariationSalesResult> lookupVariationSales(Long productId, LocalDateTime start, LocalDateTime end) {
        return lookupVariationSales(reportOrderVariationSalesQueue, "order variation sales", productId, start, end);
    }

    /**
     * Fetch variant-level sales aggregates from the POS module (completed carts) for one product.
     */
    public List<VariationSalesResult> lookupPosVariationSales(Long productId, LocalDateTime start, LocalDateTime end) {
        return lookupVariationSales(reportPosVariationSalesQueue, "POS variation sales", productId, start, end);
    }

    /**
     * Shared variant-sales round-trip used by both the order and POS variation lookups.
     * The two sources return identical row shapes, so only the queue and log label differ.
     */
    private List<VariationSalesResult> lookupVariationSales(
            String queue, String source, Long productId, LocalDateTime start, LocalDateTime end) {
        if (productId == null) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> request = baseWindowRequest(start, end);
            request.put("productId", productId);
            return sendRowsRequest(queue, source, request).stream()
                    .map(this::toVariationSalesResult)
                    .toList();
        } catch (Exception e) {
            log.error("{} lookup failed for product {} and window {} - {}", source, productId, start, end, e);
            return Collections.emptyList();
        }
    }

    private ProductSalesResult toProductSalesResult(Map<String, Object> row) {
        return new ProductSalesResult(
                toLong(row.get("productId")),
                toStringValue(row.get("date")),
                toStringValue(row.get("time")),
                toLongValue(row.get("unitsSold")),
                toBigDecimalValue(row.get("totalRevenue")),
                toBigDecimalValue(row.get("minUnitPrice")),
                toBigDecimalValue(row.get("maxUnitPrice")),
                toStringValue(row.get("productTitle")));
    }

    private VariationSalesResult toVariationSalesResult(Map<String, Object> row) {
        return new VariationSalesResult(
                toLong(row.get("variantId")),
                toLongValue(row.get("sold")),
                toBigDecimalValue(row.get("totalRevenue")),
                toBigDecimalValue(row.get("minUnitPrice")),
                toBigDecimalValue(row.get("maxUnitPrice")),
                toStringValue(row.get("variantTitle")));
    }

    private Map<String, Object> baseWindowRequest(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("start", start.toString());
        request.put("end", end.toString());
        return request;
    }

    /**
     * Send a {@code rows}-returning RPC request and extract the row maps, or an empty
     * list on a null/failed response. Shared by the product and variation sales lookups.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sendRowsRequest(String queue, String source, Map<String, Object> request) {
        Object rawResponse = rabbitTemplate.convertSendAndReceive(DEFAULT_EXCHANGE, queue, request);
        if (rawResponse == null) {
            log.warn("No response from {} lookup queue", source);
            return Collections.emptyList();
        }

        Map<String, Object> response = objectMapper.convertValue(rawResponse, Map.class);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            log.warn("{} lookup returned failure: {}", source, response.get("error"));
            return Collections.emptyList();
        }

        List<Map<String, Object>> rows = objectMapper.convertValue(response.get("rows"), List.class);
        return rows != null ? rows : Collections.emptyList();
    }

    /**
     * Resolve product title and type for the given product ids in a single
     * bulk RabbitMQ round-trip. Missing products are simply absent from the map.
     * Time complexity: O(n) over the requested ids (one network round-trip).
     */
    public Map<Long, ProductLookupInfo> lookupProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // De-duplicate ids while building the request items (variantId omitted for product-level lookup).
        List<InventoryProductLookupRequest.LookupItem> items = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> InventoryProductLookupRequest.LookupItem.builder()
                        .productId(id)
                        .build())
                .toList();

        Map<String, InventoryProductLookupResponse> responses = bulkLookup(items);

        Map<Long, ProductLookupInfo> result = new HashMap<>();
        for (Long productId : productIds) {
            if (productId == null) {
                continue;
            }
            InventoryProductLookupResponse response = responses.get(lookupKey(productId, null));
            boolean found = response != null && Boolean.TRUE.equals(response.getFound());
            result.put(productId, new ProductLookupInfo(
                    productId,
                    found ? response.getProductTitle() : null,
                    found && response.getProductType() != null ? response.getProductType() : "SIMPLE",
                    found ? response.getCategoryId() : null,
                    found ? response.getCategory() : null,
                    found ? response.getImage() : null,
                    found));
        }
        return result;
    }

    /**
     * Resolve SKU and attribute summary for the given variants of one product
     * in a single bulk RabbitMQ round-trip. Missing variants are absent.
     * Time complexity: O(n) over the requested variant ids (one round-trip).
     */
    public Map<Long, VariantLookupInfo> lookupVariants(Long productId, Collection<Long> variantIds) {
        if (productId == null || variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<InventoryProductLookupRequest.LookupItem> items = variantIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(variantId -> InventoryProductLookupRequest.LookupItem.builder()
                        .productId(productId)
                        .variantId(variantId)
                        .build())
                .toList();

        Map<String, InventoryProductLookupResponse> responses = bulkLookup(items);

        Map<Long, VariantLookupInfo> result = new LinkedHashMap<>();
        for (Long variantId : variantIds) {
            if (variantId == null) {
                continue;
            }
            InventoryProductLookupResponse response = responses.get(lookupKey(productId, variantId));
            if (response != null && Boolean.TRUE.equals(response.getFound())) {
                result.put(variantId, new VariantLookupInfo(
                        variantId,
                        response.getSku(),
                        response.getAttributeSummary()));
            }
        }
        return result;
    }

    /**
     * Resolve the available stock quantity for a product/variant from the
     * inventory module. Returns 0 when the stock record is missing or the
     * lookup fails, so report rows degrade gracefully.
     * Time complexity: O(1) (single RabbitMQ round-trip).
     */
    public int lookupStock(Long productId, Long variantId) {
        if (productId == null) {
            return 0;
        }

        try {
            StockCheckRequest request = StockCheckRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .productId(productId)
                    .variantId(variantId)
                    // Quantity is required by the consumer; 0 only queries availability.
                    .requestedQuantity(0)
                    .build();

            Object rawResponse = rabbitTemplate.convertSendAndReceive(
                    inventoryExchange, STOCK_CHECK_ROUTING_KEY, request);
            StockCheckResponse response = convertResponse(rawResponse, StockCheckResponse.class);

            if (response != null && response.getAvailableStock() != null) {
                return response.getAvailableStock();
            }
            return 0;
        } catch (Exception e) {
            log.error("Stock lookup failed - productId: {}, variantId: {}", productId, variantId, e);
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private long toLongValue(Object value) {
        Long parsed = toLong(value);
        return parsed != null ? parsed : 0L;
    }

    private BigDecimal toBigDecimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private String toStringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Send a bulk product/variant lookup and return the keyed response map,
     * or an empty map on any failure.
     */
    private Map<String, InventoryProductLookupResponse> bulkLookup(
            List<InventoryProductLookupRequest.LookupItem> items) {
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            InventoryProductLookupRequest request = InventoryProductLookupRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .items(items)
                    .build();

            Object rawResponse = rabbitTemplate.convertSendAndReceive(
                    productExchange, PRODUCT_BULK_LOOKUP_ROUTING_KEY, request);
            InventoryProductBulkLookupResponse response =
                    convertResponse(rawResponse, InventoryProductBulkLookupResponse.class);

            if (response != null && Boolean.TRUE.equals(response.getSuccess()) && response.getItems() != null) {
                return response.getItems();
            }
            log.warn("Bulk product lookup returned no items for {} request item(s)", items.size());
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Bulk product lookup failed for {} request item(s)", items.size(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Mirror of the bulk consumer's response-map key: {@code productId:variantId},
     * with the literal {@code null} for product-level lookups.
     */
    private String lookupKey(Long productId, Long variantId) {
        return productId + ":" + (variantId == null ? "null" : variantId);
    }

    /**
     * Coerce the raw RabbitMQ reply into the target type. The reply may already
     * be the typed object, raw JSON bytes, or a JSON string depending on the
     * responding consumer.
     */
    private <T> T convertResponse(Object rawResponse, Class<T> targetType) {
        if (rawResponse == null) {
            return null;
        }
        if (targetType.isInstance(rawResponse)) {
            return targetType.cast(rawResponse);
        }
        try {
            if (rawResponse instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, targetType);
            }
            if (rawResponse instanceof String json) {
                return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), targetType);
            }
            return objectMapper.convertValue(rawResponse, targetType);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to deserialize RabbitMQ response to " + targetType.getSimpleName(), e);
        }
    }
}
