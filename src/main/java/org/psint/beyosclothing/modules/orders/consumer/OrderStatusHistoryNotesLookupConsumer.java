package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Order Status History Notes Lookup Consumer
 * Handles order status history notes lookup requests from Reseller module via RabbitMQ
 * Maps to routing key: order.status.history.notes.lookup.request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusHistoryNotesLookupConsumer {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.order-status-history-notes-lookup-request:order.status.history.notes.lookup.request}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public Map<String, Object> handleOrderStatusHistoryNotesLookup(Map<String, Object> request) {
        log.info("=== ORDER STATUS HISTORY NOTES LOOKUP CONSUMER TRIGGERED ===");
        log.info("Received order status history notes lookup request for order UUID: {}", request.get("orderUuid"));

        try {
            // Extract request parameters
            String orderUuid = (String) request.get("orderUuid");
            Long resellerId = getLongValue(request.get("resellerId"));

            // Validate required fields
            if (orderUuid == null || orderUuid.isBlank()) {
                log.warn("Invalid order status history notes lookup request - missing orderUuid");
                return buildNotFoundResponse();
            }

            // Fetch order from database
            Optional<OrderEntity> orderOptional = orderRepository.findByUuid(orderUuid);

            if (orderOptional.isEmpty()) {
                log.warn("Order not found with UUID: {}", orderUuid);
                return buildNotFoundResponse();
            }

            OrderEntity order = orderOptional.get();

            // Verify order belongs to the requesting reseller (if resellerId is provided)
            if (resellerId != null && order.getResellerId() != null &&
                    !order.getResellerId().equals(resellerId)) {
                log.warn("Reseller {} attempted to access order {} that doesn't belong to them",
                        resellerId, orderUuid);
                return buildForbiddenResponse();
            }

            // Fetch order status history (ordered by created_at DESC)
            List<OrderStatusHistoryEntity> statusHistory =
                    orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());

            // Build response
            return buildSuccessResponse(order, statusHistory);

        } catch (Exception e) {
            log.error("Error processing order status history notes lookup request", e);
            return buildErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildSuccessResponse(
            OrderEntity order,
            List<OrderStatusHistoryEntity> statusHistory) {

        Map<String, Object> response = new HashMap<>();
        response.put("found", true);
        response.put("success", true);
        response.put("belongsToReseller", true);

        // Basic order info (kept minimal)
        response.put("orderId", order.getId());
        response.put("orderUuid", order.getUuid());
        response.put("orderNumber", order.getOrderNumber());

        // Filter for REJECT status entries only and get the latest
        List<OrderStatusHistoryEntity> rejectEntries = statusHistory.stream()
                .filter(history -> history.getNewStatus() != null &&
                        history.getNewStatus().equalsIgnoreCase("REJECTED"))
                .toList();

        // Extract note from the latest REJECT entry
        List<String> rejectNotes = new ArrayList<>();
        String reasonForCancellation = null;
        if (!rejectEntries.isEmpty()) {
            OrderStatusHistoryEntity latestReject = rejectEntries.getFirst();
            if (latestReject.getNotes() != null) {
                rejectNotes.add(latestReject.getNotes());
                reasonForCancellation = latestReject.getNotes();
            }
        }

        // Also check for CANCELLED status entries as fallback for cancellation reason
        if (reasonForCancellation == null) {
            List<OrderStatusHistoryEntity> cancelEntries = statusHistory.stream()
                    .filter(history -> history.getNewStatus() != null &&
                            (history.getNewStatus().equalsIgnoreCase("CANCELLED") ||
                             history.getNewStatus().equalsIgnoreCase("CANCEL")))
                    .toList();
            if (!cancelEntries.isEmpty()) {
                OrderStatusHistoryEntity latestCancel = cancelEntries.getFirst();
                if (latestCancel.getNotes() != null) {
                    reasonForCancellation = latestCancel.getNotes();
                }
            }
        }

        response.put("notes", rejectNotes);
        response.put("totalNotes", rejectNotes.size());
        response.put("hasReject", !rejectNotes.isEmpty());
        response.put("reasonForCancellation", reasonForCancellation);

        log.info("Order status history notes - UUID: {}, hasReject: {}, reasonForCancellation: {}",
                order.getUuid(), !rejectNotes.isEmpty(), reasonForCancellation);
        return response;
    }

    private Map<String, Object> buildNotFoundResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("found", false);
        response.put("success", false);
        response.put("message", "Order not found");
        return response;
    }

    private Map<String, Object> buildForbiddenResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("found", true);
        response.put("success", false);
        response.put("belongsToReseller", false);
        response.put("message", "Order does not belong to this reseller");
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

