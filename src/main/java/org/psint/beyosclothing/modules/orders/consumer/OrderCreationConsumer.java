package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.*;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderShippingAddressRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Order Creation Consumer
 * Handles order creation requests from Reseller and POS modules via RabbitMQ RPC
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreationConsumer {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository shippingAddressRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.order-create-request:order.create.request}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public Map<String, Object> handleOrderCreation(Map<String, Object> request) {
        log.info("=== ORDER CREATION CONSUMER TRIGGERED ===");
        log.info("Received order creation request - Type: {}, Reseller: {}, Customer: {}",
                request.get("orderType"), request.get("resellerUuid"), request.get("customerUuid"));

        try {
            // Extract request data
            Long resellerId = getLongValue(request.get("resellerId"));
            Long customerId = getLongValue(request.get("customerId"));
            String orderType = (String) request.get("orderType");
            String customerName = (String) request.get("customerName");
            Long cartId = getLongValue(request.get("cartId"));
            String customerPhone = (String) request.get("customerPhone");
            String customerEmail = (String) request.get("customerEmail");
            String courierUuid = (String) request.get("courierUuid");
            String paymentMethod = (String) request.get("paymentMethod");
            String notes = (String) request.get("notes");

            BigDecimal subtotal = getBigDecimalValue(request.get("subtotal"));
            BigDecimal deliveryCharges = getBigDecimalValue(request.get("deliveryCharges"));
            BigDecimal promoDiscount = getBigDecimalValue(request.get("promoDiscount"));
            BigDecimal total = getBigDecimalValue(request.get("total"));

            @SuppressWarnings("unchecked")
            Map<String, Object> shippingAddressData = (Map<String, Object>) request.get("shippingAddress");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsData = (List<Map<String, Object>>) request.get("items");

            // Extract POS-specific fields
            String sourceStr = (String) request.get("source");
            OrderEntity.OrderSource source = "POS".equalsIgnoreCase(sourceStr)
                    ? OrderEntity.OrderSource.POS : OrderEntity.OrderSource.ONLINE;
            OrderItemEntity.OrderItemSource itemSource = "POS".equalsIgnoreCase(sourceStr)
                    ? OrderItemEntity.OrderItemSource.POS : OrderItemEntity.OrderItemSource.ONLINE;
            String orderUuid = request.get("orderUuid") != null
                    ? (String) request.get("orderUuid") : UUID.randomUUID().toString();

            Optional<OrderEntity> existingOrder = findExistingOrder(source, cartId, orderUuid);
            if (existingOrder.isPresent()) {
                OrderEntity order = existingOrder.get();
                log.warn("Order creation request already handled - source={}, cartId={}, orderId={}, orderNumber={}",
                        source, cartId, order.getId(), order.getOrderNumber());
                return buildSuccessResponse(order);
            }

            // Validate required fields (POS walk-in orders allow null customerId)
            boolean isPosOrder = source == OrderEntity.OrderSource.POS;
            if ((!isPosOrder && resellerId == null && customerId == null) || itemsData == null || itemsData.isEmpty()) {
                log.error("Invalid order creation request - missing required fields");
                return buildErrorResponse("Missing required fields: customerId/resellerId or items");
            }

            // Generate order number
            String orderNumber = generateOrderNumber();
            Long posTerminalId = getLongValue(request.get("posTerminalId"));
            Long posCashierId = getLongValue(request.get("posCashierId"));
            String cardLastFourDigits = (String) request.get("cardLastFourDigits");
            Long paymentMethodId = getLongValue(request.get("paymentMethodId"));
            BigDecimal shippingWeight = getBigDecimalValue(request.get("shippingWeight"));
            String shippingPayerStr = (String) request.get("shippingPayer");

            @SuppressWarnings("unchecked")
            Map<String, Object> shippingBreakdown = (Map<String, Object>) request.get("shippingBreakdown");

            // Determine shipping payer
            OrderEntity.ShippingPayer shippingPayer;
            if (shippingPayerStr != null) {
                shippingPayer = OrderEntity.ShippingPayer.valueOf(shippingPayerStr);
            } else {
                shippingPayer = resellerId != null
                        ? OrderEntity.ShippingPayer.RESELLER : OrderEntity.ShippingPayer.CUSTOMER;
            }

            // Create Order entity
            OrderEntity order = OrderEntity.builder()
                    .uuid(orderUuid)
                    .customerId(customerId)
                    .resellerId(resellerId)
                    .cartId(cartId)
                    .orderNumber(orderNumber)
                    .source(source)
                    .posTerminalId(posTerminalId)
                    .posCashierId(posCashierId)
                    .cardLastFourDigits(cardLastFourDigits)
                    .status(OrderEntity.OrderStatus.PENDING)
                    .paymentStatus(OrderEntity.PaymentStatus.UNPAID)
                    .paymentMethod(paymentMethod != null ? paymentMethod : "COD")
                    .paymentMethodId(paymentMethodId)
                    .subtotal(subtotal)
                    .discountTotal(promoDiscount)
                    .shippingCost(deliveryCharges)
                    .shippingWeight(shippingWeight)
                    .shippingBreakdown(shippingBreakdown)
                    .shippingPayer(shippingPayer)
                    .total(total)
                    .promoDiscount(promoDiscount)
                    .notes(notes)
                    .build();

            // Save order
            order = orderRepository.save(order);
            log.info("Order created with ID: {}, Number: {}", order.getId(), order.getOrderNumber());

            // Create shipping address
            if (shippingAddressData != null) {
                createShippingAddress(order.getId(), customerName, customerPhone, customerEmail, shippingAddressData);
            }

            // Create order items
            for (Map<String, Object> itemData : itemsData) {
                createOrderItem(order.getId(), itemData, itemSource);
            }

            // Create status history
            OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                    .orderId(order.getId())
                    .oldStatus(null)
                    .newStatus(OrderEntity.OrderStatus.PENDING.name())
                    .notes(source == OrderEntity.OrderSource.POS ? "POS order placed" : "Order placed")
                    .build();
            statusHistoryRepository.save(history);

            log.info("Order creation completed - Order ID: {}, Number: {}, Items: {}",
                    order.getId(), order.getOrderNumber(), itemsData.size());

            // Return success response
            return buildSuccessResponse(order);

        } catch (Exception e) {
            log.error("Error creating order", e);
            return buildErrorResponse("Error creating order: " + e.getMessage());
        }
    }

    private Optional<OrderEntity> findExistingOrder(OrderEntity.OrderSource source, Long cartId, String orderUuid) {
        if (orderUuid != null && !orderUuid.isBlank()) {
            Optional<OrderEntity> orderByUuid = orderRepository.findByUuid(orderUuid);
            if (orderByUuid.isPresent()) {
                return orderByUuid;
            }
        }
        if (source == OrderEntity.OrderSource.POS && cartId != null) {
            return orderRepository.findFirstBySourceAndCartIdOrderByCreatedAtDesc(source, cartId);
        }
        return Optional.empty();
    }

    private void createShippingAddress(Long orderId, String customerName, String customerPhone,
                                       String customerEmail, Map<String, Object> addressData) {
        try {
            OrderShippingAddressEntity shippingAddress = OrderShippingAddressEntity.builder()
                    .orderId(orderId)
                    .fullName(customerName)
                    .phone(customerPhone)
                    .email(customerEmail)
                    .addressLine1((String) addressData.get("line1"))
                    .addressLine2((String) addressData.get("line2"))
                    .city((String) addressData.get("city"))
                    //districtId need to convert to Long
                    .districtId(
                            getLongValue(addressData.get("districtId"))
                    )
                    .cityId(getLongValue(addressData.get("cityId")))
                    .province((String) addressData.get("province"))
                    .postalCode((String) addressData.get("postalCode"))
                    .build();

            shippingAddressRepository.save(shippingAddress);
            log.debug("Shipping address created for order ID: {}", orderId);
        } catch (Exception e) {
            log.error("Error creating shipping address for order ID: {}", orderId, e);
            // Don't throw - address is not critical for order creation
        }
    }

    private void createOrderItem(Long orderId, Map<String, Object> itemData, OrderItemEntity.OrderItemSource itemSource) {
        Long productId = getLongValue(itemData.get("productId"));
        Long variantId = getLongValue(itemData.get("variantId"));
        String productName = (String) itemData.get("productName");
        String variantName = (String) itemData.get("variantName");
        Integer quantity = getIntegerValue(itemData.get("quantity"));
        BigDecimal unitPrice = getBigDecimalValue(itemData.get("unitPrice"));
        BigDecimal totalPrice = getBigDecimalValue(itemData.get("totalPrice"));
        BigDecimal itemWeight = getBigDecimalValue(itemData.get("itemWeight"));
        BigDecimal itemTotalWeight = getBigDecimalValue(itemData.get("itemTotalWeight"));

        OrderItemEntity orderItem = OrderItemEntity.builder()
                .uuid(UUID.randomUUID().toString())
                .orderId(orderId)
                .productId(productId)
                .variantId(variantId)
                .productTitle(productName)
                .variantTitle(variantName)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .itemWeight(itemWeight)
                .itemTotalWeight(itemTotalWeight)
                .source(itemSource)
                .isRefunded(false)
                .build();

        orderItemRepository.save(orderItem);
        log.debug("Order item created - Order ID: {}, Product: {}, Quantity: {}",
                orderId, productName, quantity);
    }

    private String generateOrderNumber() {
        // Generate format: ORD-YYYYMMDD-XXXXX
        String datePrefix = LocalDateTime.now().toString().substring(0, 10).replace("-", "");
        String randomSuffix = String.format("%05d", new Random().nextInt(100000));
        return "ORD-" + datePrefix + "-" + randomSuffix;
    }

    private Map<String, Object> buildSuccessResponse(OrderEntity order) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderId", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("orderUuid", order.getUuid());
        response.put("orderDate", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        response.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        response.put("message", "Order created successfully");
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

    private BigDecimal getBigDecimalValue(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Double) return BigDecimal.valueOf((Double) value);
        if (value instanceof Integer) return BigDecimal.valueOf((Integer) value);
        if (value instanceof Long) return BigDecimal.valueOf((Long) value);
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }
}
