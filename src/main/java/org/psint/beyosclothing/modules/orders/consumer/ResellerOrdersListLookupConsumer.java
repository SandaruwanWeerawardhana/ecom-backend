package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderShippingAddressEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderShippingAddressRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reseller Orders List Lookup Consumer
 * Handles reseller orders list lookup requests from Reseller module via RabbitMQ
 * Maps to routing key: reseller.orders.list.lookup.request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerOrdersListLookupConsumer {

    private final OrderRepository orderRepository;
    private final OrderShippingAddressRepository shippingAddressRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.reseller-orders-list-lookup-request:reseller.orders.list.lookup.request}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public Map<String, Object> handleResellerOrdersListLookup(Map<String, Object> request) {
        log.info("=== RESELLER ORDERS LIST LOOKUP CONSUMER TRIGGERED ===");
        log.info("Received orders list lookup request for reseller ID: {}", request.get("resellerId"));

        try {
            // Extract request parameters
            Long resellerId = getLongValue(request.get("resellerId"));
            String search = (String) request.get("search");
            String status = (String) request.get("status");
            String startDateStr = (String) request.get("startDate");
            String endDateStr = (String) request.get("endDate");
            Integer page = getIntegerValue(request.get("page"));
            Integer size = getIntegerValue(request.get("size"));

            // Validate required fields
            if (resellerId == null) {
                log.error("Invalid reseller orders list lookup request - missing resellerId");
                return buildErrorResponse("Missing required field: resellerId");
            }

            // Set defaults
            page = (page != null && page >= 0) ? page : 0;
            size = (size != null && size > 0 && size <= 100) ? size : 10;

            log.info("Fetching orders for reseller ID: {} - search: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                    resellerId, search, status, startDateStr, endDateStr, page, size);

            // Parse dates
            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;

            if (startDateStr != null && !startDateStr.isBlank()) {
                try {
                    LocalDate startDate = LocalDate.parse(startDateStr);
                    startDateTime = startDate.atStartOfDay();
                } catch (Exception e) {
                    log.warn("Invalid startDate format: {}", startDateStr);
                }
            }

            if (endDateStr != null && !endDateStr.isBlank()) {
                try {
                    LocalDate endDate = LocalDate.parse(endDateStr);
                    endDateTime = endDate.atTime(LocalTime.MAX);
                } catch (Exception e) {
                    log.warn("Invalid endDate format: {}", endDateStr);
                }
            }

            // Normalize search and status
            String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
            String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

            // Validate status if provided
            OrderEntity.OrderStatus orderStatus = null;
            if (normalizedStatus != null) {
                try {
                    orderStatus = OrderEntity.OrderStatus.valueOf(normalizedStatus);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid order status: {}", normalizedStatus);
                    orderStatus = null;
                }
            }

            // Create pageable
            Pageable pageable = PageRequest.of(page, size);

            // Fetch orders with filters
            Page<OrderEntity> ordersPage = orderRepository.findResellerOrdersWithFilters(
                    resellerId,
                    normalizedSearch,
                    orderStatus,
                    startDateTime,
                    endDateTime,
                    pageable
            );

            log.info("Orders fetched - Total: {}, Pages: {}, Current Page: {}",
                    ordersPage.getTotalElements(), ordersPage.getTotalPages(), ordersPage.getNumber());

            // Map to response DTOs
            List<Map<String, Object>> orderSummaries = ordersPage.getContent().stream()
                    .map(this::mapToOrderSummary)
                    .collect(Collectors.toList());

            // Build success response
            return buildSuccessResponse(
                    orderSummaries,
                    ordersPage.getTotalElements(),
                    ordersPage.getTotalPages(),
                    ordersPage.getNumber(),
                    ordersPage.getSize(),
                    ordersPage.isFirst(),
                    ordersPage.isLast(),
                    ordersPage.isEmpty()
            );

        } catch (Exception e) {
            log.error("Error handling reseller orders list lookup request", e);
            return buildErrorResponse("Error processing request: " + e.getMessage());
        }
    }

    private Map<String, Object> mapToOrderSummary(OrderEntity order) {
        Map<String, Object> summary = new HashMap<>();

        // Get customer name from shipping address
        String customerName = shippingAddressRepository.findByOrderId(order.getId())
                .map(OrderShippingAddressEntity::getFullName)
                .orElse("N/A");

        String reasonForCancellation = getReasonForCancellation(order.getId());

        summary.put("orderUuid", order.getUuid());
        summary.put("orderNumber", order.getOrderNumber());
        summary.put("orderDate", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        summary.put("customerName", customerName);
        summary.put("amount", order.getTotal());
        summary.put("profit", calculateProfit(order)); // Will be calculated from price overrides
        summary.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        summary.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        summary.put("reasonForCancellation", reasonForCancellation);

        return summary;
    }

    private String getReasonForCancellation(Long orderId) {
        // Check for REJECTED status entries first
        List<OrderStatusHistoryEntity> rejectEntries = orderStatusHistoryRepository
                .findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(history -> history.getNewStatus() != null &&
                        history.getNewStatus().equalsIgnoreCase("REJECTED"))
                .toList();

        if (!rejectEntries.isEmpty()) {
            OrderStatusHistoryEntity latestReject = rejectEntries.getFirst();
            if (latestReject.getNotes() != null) {
                return latestReject.getNotes();
            }
        }

        // Fallback to CANCELLED status entries
        List<OrderStatusHistoryEntity> cancelEntries = orderStatusHistoryRepository
                .findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(history -> history.getNewStatus() != null &&
                        (history.getNewStatus().equalsIgnoreCase("CANCELLED") ||
                         history.getNewStatus().equalsIgnoreCase("CANCEL")))
                .toList();

        if (!cancelEntries.isEmpty()) {
            OrderStatusHistoryEntity latestCancel = cancelEntries.getFirst();
            if (latestCancel.getNotes() != null) {
                return latestCancel.getNotes();
            }
        }

        return null;
    }

    private BigDecimal calculateProfit(OrderEntity order) {
        // TODO: Fetch profit from reseller_price_override table or calculate based on base price
        // For now, returning ZERO - this should be enhanced with actual profit calculation
        return BigDecimal.ZERO;
    }

    private Map<String, Object> buildSuccessResponse(
            List<Map<String, Object>> orders,
            Long totalElements,
            Integer totalPages,
            Integer pageNumber,
            Integer pageSize,
            Boolean isFirst,
            Boolean isLast,
            Boolean isEmpty) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orders", orders);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("pageNumber", pageNumber);
        response.put("pageSize", pageSize);
        response.put("first", isFirst);
        response.put("last", isLast);
        response.put("empty", isEmpty);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }

    // Utility methods for type conversion
    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

