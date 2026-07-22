package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles customer order count lookups from the customer module.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderCountsLookupConsumer {

    private final OrderRepository orderRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.customer-order-counts-lookup-request:customer.order.counts.lookup.request}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public Map<String, Object> handleCustomerOrderCountsLookup(Map<String, Object> request) {
        try {
            Long customerId = getLong(request.get("customerId"));
            if (customerId == null) {
                return buildErrorResponse("Missing customerId in request");
            }

            Long totalOrdersCount = orderRepository.countByCustomerId(customerId);
            Long pendingOrdersCount = orderRepository.countPendingOrdersByCustomerId(customerId);
            Long completedOrdersCount = orderRepository.countCompletedOrdersByCustomerId(customerId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", request.get("requestId"));
            response.put("customerId", customerId);
            response.put("totalOrdersCount", totalOrdersCount != null ? totalOrdersCount : 0L);
            response.put("pendingOrdersCount", pendingOrdersCount != null ? pendingOrdersCount : 0L);
            response.put("completedOrdersCount", completedOrdersCount != null ? completedOrdersCount : 0L);
            return response;
        } catch (Exception e) {
            log.error("Error handling customer order counts lookup", e);
            return buildErrorResponse("Error fetching customer order counts: " + e.getMessage());
        }
    }

    private Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Map<String, Object> buildErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }
}
