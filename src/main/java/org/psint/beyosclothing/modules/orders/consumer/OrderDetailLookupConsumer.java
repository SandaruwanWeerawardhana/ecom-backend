package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.*;
import org.psint.beyosclothing.modules.orders.repository.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order Detail Lookup Consumer
 * Handles order detail lookup requests from Reseller module via RabbitMQ
 * Maps to routing key: order.detail.lookup.request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderDetailLookupConsumer {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository shippingAddressRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.order-detail-lookup-request:order.detail.lookup.request}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public Map<String, Object> handleOrderDetailLookup(Map<String, Object> request) {
        log.info("=== ORDER DETAIL LOOKUP CONSUMER TRIGGERED ===");
        log.info("Received order detail lookup request for order UUID: {}", request.get("orderUuid"));

        try {
            // Extract request parameters
            String orderUuid = (String) request.get("orderUuid");
            Long resellerId = getLongValue(request.get("resellerId"));
            Long customerId = getLongValue(request.get("customerId"));

            if (orderUuid == null || orderUuid.isBlank()) {
                if (customerId == null) {
                    log.warn("Invalid order detail lookup request - missing orderUuid and customerId");
                    return buildNotFoundResponse();
                }
                List<OrderEntity> orders = orderRepository
                        .findByCustomerIdOrderByCreatedAtDesc(customerId, org.springframework.data.domain.Pageable.unpaged())
                        .getContent();

                if (orders.isEmpty()) {
                    log.warn("No orders found for customerId: {}", customerId);
                    return buildNotFoundResponse();
                }

                return buildCustomerOrdersResponse(orders);
            }

            Optional<OrderEntity> orderOptional = orderRepository.findByUuid(orderUuid);

            if (orderOptional.isEmpty()) {
                log.warn("Order not found for UUID: {}, customerId: {}", orderUuid, customerId);
                return buildNotFoundResponse();
            }

            OrderEntity order = orderOptional.get();

            // Verify order belongs to the requesting reseller or customer
            boolean belongsToReseller = order.getResellerId() != null &&
                                       order.getResellerId().equals(resellerId);
            boolean belongsToCustomer = order.getCustomerId() != null &&
                                       order.getCustomerId().equals(customerId);

            if (!belongsToReseller && !belongsToCustomer) {
                log.warn("Requester attempted to access order {} without ownership. resellerId={}, customerId={}",
                        orderUuid, resellerId, customerId);
                return buildForbiddenResponse();
            }

            // Fetch related data
            List<OrderItemEntity> orderItems = orderItemRepository.findByOrderId(order.getId());
            OrderShippingAddressEntity shippingAddress =
                shippingAddressRepository.findByOrderId(order.getId()).orElse(null);
            List<OrderPaymentEntity> payments = orderPaymentRepository.findByOrderId(order.getId());
            List<OrderStatusHistoryEntity> statusHistory =
                orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());

            // Build response
            return buildSuccessResponse(order, orderItems, shippingAddress, payments, statusHistory);

        } catch (Exception e) {
            log.error("Error processing order detail lookup request", e);
            return buildErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildCustomerOrdersResponse(List<OrderEntity> orders) {
        List<Map<String, Object>> orderResponses = orders.stream()
                .map(order -> {
                    List<OrderItemEntity> orderItems = orderItemRepository.findByOrderId(order.getId());
                    OrderShippingAddressEntity shippingAddress =
                            shippingAddressRepository.findByOrderId(order.getId()).orElse(null);
                    List<OrderPaymentEntity> payments = orderPaymentRepository.findByOrderId(order.getId());
                    List<OrderStatusHistoryEntity> statusHistory =
                            orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());

                    return buildSuccessResponse(order, orderItems, shippingAddress, payments, statusHistory);
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("found", true);
        response.put("success", true);
        response.put("orders", orderResponses);
        return response;
    }

    private Map<String, Object> buildSuccessResponse(
            OrderEntity order,
            List<OrderItemEntity> items,
            OrderShippingAddressEntity shippingAddress,
            List<OrderPaymentEntity> payments,
            List<OrderStatusHistoryEntity> statusHistory) {

        Map<String, Object> response = new HashMap<>();
        response.put("found", true);
        response.put("belongsToReseller", order.getResellerId() != null);
        response.put("belongsToCustomer", order.getCustomerId() != null);
        response.put("success", true);

        // Basic order info
        response.put("orderId", order.getId());
        response.put("orderUuid", order.getUuid());
        response.put("orderNumber", order.getOrderNumber());
        response.put("orderDate", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        response.put("orderStatus", order.getStatus() != null ? order.getStatus().toString() : null);
        response.put("orderFrom", order.getResellerId() != null ? "RESELLER" : "CUSTOMER");
        response.put("orderType", order.getSource() != null ? order.getSource().toString() : null);
        response.put("transactionId", order.getPaymentReference() != null ? order.getPaymentReference() : null);

        // Customer info - from shipping address
        Map<String, Object> customer = new HashMap<>();
        if (shippingAddress != null) {
            customer.put("fullName", shippingAddress.getFullName());
            customer.put("phone", shippingAddress.getPhone());
            customer.put("email", shippingAddress.getEmail());
            customer.put("addressLine1", shippingAddress.getAddressLine1());
            customer.put("addressLine2", shippingAddress.getAddressLine2());
            customer.put("city", shippingAddress.getCity());
            customer.put("district", shippingAddress.getCity()); // Using city as district fallback
            customer.put("province", shippingAddress.getProvince());
            customer.put("postalCode", shippingAddress.getPostalCode());
            customer.put("country", shippingAddress.getCountry());
        }
        response.put("customer", customer);

        // Shipping address
        Map<String, Object> shippingAddr = new HashMap<>();
        if (shippingAddress != null) {
            shippingAddr.put("line1", shippingAddress.getAddressLine1());
            shippingAddr.put("line2", shippingAddress.getAddressLine2());
            shippingAddr.put("city", shippingAddress.getCity());
            shippingAddr.put("district", shippingAddress.getCity()); // Using city as district fallback
            shippingAddr.put("province", shippingAddress.getProvince());
            shippingAddr.put("postalCode", shippingAddress.getPostalCode());
        }
        response.put("shippingAddress", shippingAddr);

        // Order items - map from OrderItemEntity
        List<Map<String, Object>> itemsList = items.stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("productName", item.getProductTitle());
                    itemMap.put("variantName", item.getVariantTitle());
                    itemMap.put("unitPrice", item.getUnitPrice());
                    itemMap.put("basePrice", item.getUnitBasePrice() != null ? item.getUnitBasePrice() : item.getUnitPrice());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("totalPrice", item.getTotalPrice());
                    itemMap.put("margin", item.getResellerMarginAmount() != null ? item.getResellerMarginAmount() : BigDecimal.ZERO);
                    itemMap.put("isRefunded", item.getIsRefunded() != null ? item.getIsRefunded() : false);
                    return itemMap;
                })
                .collect(Collectors.toList());
        response.put("items", itemsList);

        // Financial info
        response.put("subtotal", order.getSubtotal());
        response.put("shippingCost", order.getShippingCost());
        response.put("discountTotal", order.getDiscountTotal());
        response.put("tax", BigDecimal.ZERO); // Tax not in OrderEntity
        response.put("total", order.getTotal());

        // Payment info - from OrderPaymentEntity
        Map<String, Object> payment = new HashMap<>();
        if (!payments.isEmpty()) {
            // Get the latest payment
            OrderPaymentEntity latestPayment = payments.get(0);
            payment.put("paymentMethod", latestPayment.getMethod());
            payment.put("paymentStatus", latestPayment.getStatus() != null ? latestPayment.getStatus().toString() : null);
            payment.put("transactionId", latestPayment.getGatewayTransactionId());
            payment.put("paidAmount", latestPayment.getAmount());
            payment.put("paymentDate", latestPayment.getPaidAt() != null ? latestPayment.getPaidAt().toString() : null);
        } else {
            // Fallback to order payment method
            payment.put("paymentMethod", order.getPaymentMethod());
            payment.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().toString() : null);
            payment.put("transactionId", order.getPaymentReference());
            payment.put("paidAmount", BigDecimal.ZERO);
            payment.put("paymentDate", null);
        }
        response.put("payment", payment);

        // Tracking info - OrderEntity doesn't have tracking fields, so return empty
        response.put("tracking", null);

        // Order history - from OrderStatusHistoryEntity
        List<Map<String, Object>> historyList = statusHistory.stream()
                .map(history -> {
                    Map<String, Object> historyMap = new HashMap<>();
                    historyMap.put("status", history.getNewStatus());
                    historyMap.put("timestamp", history.getCreatedAt() != null ? history.getCreatedAt().toString() : null);
                    historyMap.put("changedBy", history.getChangedBy() != null ? history.getChangedBy().toString() : "SYSTEM");
                    historyMap.put("notes", history.getNotes());
                    return historyMap;
                })
                .collect(Collectors.toList());
        response.put("orderHistory", historyList);

        log.info("Order detail lookup successful for UUID: {}", order.getUuid());
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
        response.put("belongsToReseller", false);
        response.put("success", false);
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

