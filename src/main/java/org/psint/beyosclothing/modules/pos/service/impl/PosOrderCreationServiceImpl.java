package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderItemEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCartItemEntity;
import org.psint.beyosclothing.modules.pos.entity.PosReceiptEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.psint.beyosclothing.modules.pos.repository.PosReceiptRepository;
import org.psint.beyosclothing.modules.pos.service.PosOrderCreationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * POS Order Creation Service Implementation
 * Implements three-step order creation:
 * Step 1: Create order header with POS-specific fields
 * Step 2: Create order items with product snapshots and weights
 * Step 3: Ensure totals integrity and POS-specific rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosOrderCreationServiceImpl implements PosOrderCreationService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PosCartItemRepository cartItemRepository;
    private final PosReceiptRepository receiptRepository;
    private final RabbitTemplate rabbitTemplate;

    private static final String PRODUCT_DETAILS_QUEUE = "product.details.lookup.request";
    private static final long RPC_TIMEOUT_MS = 1000L;

    @Override
    @Transactional("orderTransactionManager")
    public OrderEntity createPosOrder(PosCartEntity cart, PosPlaceOrderRequest request) {
        log.info("Creating POS order from cart: {}", cart.getUuid());

        OrderEntity order = createOrderHeader(cart, request);
        createOrderItems(cart, order);
        validateTotalsIntegrity(cart, order);

        String receiptNumber = generateAndStoreReceipt(order);

        log.info("POS order created successfully - orderNumber: {}, receiptNumber: {}, orderUuid: {}",
                order.getOrderNumber(), receiptNumber, order.getUuid());
        publishInventoryDeductionEventAsync(order);

        return order;
    }

    /**
     * Step 1: Create order header with POS-specific fields
     */
    private OrderEntity createOrderHeader(PosCartEntity cart, PosPlaceOrderRequest request) {
        log.debug("Step 1: Creating order header for cart: {}", cart.getUuid());

        String orderNumber = generatePosOrderNumber();

        OrderEntity order = OrderEntity.builder()
                .orderNumber(orderNumber)
                .source(OrderEntity.OrderSource.POS)
                .posTerminalId(cart.getTerminalId())
                .posCashierId(cart.getCashierId())
                .customerId(request.getCustomerId())
                .status(OrderEntity.OrderStatus.PAID)
                .paymentStatus(OrderEntity.PaymentStatus.PAID)
                .paymentMethodId(request.getPaymentMethodId())
                .cardLastFourDigits(request.getCardLastFourDigits())
                .subtotal(cart.getSubtotal())
                .discountTotal(cart.getDiscountAmount())
                .shippingCost(BigDecimal.ZERO)
                .shippingWeight(BigDecimal.ZERO)
                .total(cart.getTotal())
                .notes(request.getCustomerNotes())
                .build();

        order = orderRepository.save(order);

        log.info("Order header created - orderNumber: {}, source: POS, status: PAID", orderNumber);
        return order;
    }

    /**
     * Step 2: Create order items with product snapshots and weights
     */
    private void createOrderItems(PosCartEntity cart, OrderEntity order) {
        log.debug("Step 2: Creating order items for order: {}", order.getOrderNumber());

        List<PosCartItemEntity> cartItems = cartItemRepository.findByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            log.warn("No cart items found for cart: {}", cart.getId());
            return;
        }

        BigDecimal totalWeight = BigDecimal.ZERO;

        for (PosCartItemEntity cartItem : cartItems) {
            OrderItemEntity orderItem = createOrderItemWithSnapshot(cartItem, order);

            orderItemRepository.save(orderItem);

            if (orderItem.getItemTotalWeight() != null) {
                totalWeight = totalWeight.add(orderItem.getItemTotalWeight());
            }

            log.debug("Order item created - product: {}, quantity: {}, weight: {}",
                    orderItem.getProductId(), orderItem.getQuantity(), orderItem.getItemTotalWeight());
        }

        order.setShippingWeight(totalWeight);
        orderRepository.save(order);

        log.info("Created {} order items with total weight: {} kg", cartItems.size(), totalWeight);
    }

    /**
     * Create order item with product snapshot and weight data
     */
    private OrderItemEntity createOrderItemWithSnapshot(PosCartItemEntity cartItem, OrderEntity order) {
        Map<String, Object> productDetails = fetchProductDetails(cartItem.getProductId(), cartItem.getVariantId());

        String productTitle = extractString(productDetails, "title", "Product #" + cartItem.getProductId());
        String variantTitle = cartItem.getVariantId() != null ?
                extractString(productDetails, "variantTitle", null) : null;
        BigDecimal itemWeight = extractWeight(productDetails);
        BigDecimal itemTotalWeight = itemWeight != null ?
                itemWeight.multiply(BigDecimal.valueOf(cartItem.getQuantity())) : null;

        return OrderItemEntity.builder()
                .source(OrderItemEntity.OrderItemSource.POS)
                .orderId(order.getId())
                .productId(cartItem.getProductId())
                .variantId(cartItem.getVariantId())
                .productTitle(productTitle)
                .variantTitle(variantTitle)
                .quantity(cartItem.getQuantity())
                .unitPrice(cartItem.getUnitPrice())
                .totalPrice(cartItem.getTotalPrice())
                .itemWeight(itemWeight)
                .itemTotalWeight(itemTotalWeight)
                .isRefunded(false)
                .build();
    }

    /**
     * Fetch product details including weight via RabbitMQ
     */
    private Map<String, Object> fetchProductDetails(Long productId, Long variantId) {
        try {
            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            Map<String, Object> request = Map.of(
                    "productId", productId,
                    "variantId", variantId != null ? variantId : 0L
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rabbitTemplate.convertSendAndReceive(
                    PRODUCT_DETAILS_QUEUE,
                    request
            );

            return response != null ? response : Map.of();

        } catch (Exception e) {
            log.warn("Failed to fetch product details for product {}: {}", productId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Extract string value from map
     */
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Extract weight from product details response
     */
    private BigDecimal extractWeight(Map<String, Object> productDetails) {
        try {
            Object weightObj = productDetails.get("weightKg");
            if (weightObj != null) {
                if (weightObj instanceof BigDecimal bigDecimal) {
                    return bigDecimal;
                } else if (weightObj instanceof Number number) {
                    return BigDecimal.valueOf(number.doubleValue());
                } else if (weightObj instanceof String string) {
                    return new BigDecimal(string);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse weight from product details: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Step 3: Validate totals integrity and POS-specific rules
     */
    private void validateTotalsIntegrity(PosCartEntity cart, OrderEntity order) {
        log.debug("Step 3: Validating totals integrity for order: {}", order.getOrderNumber());

        if (cart.getSubtotal().compareTo(order.getSubtotal()) != 0) {
            log.error("Subtotal mismatch - cart: {}, order: {}", cart.getSubtotal(), order.getSubtotal());
            throw new IllegalStateException("Order subtotal does not match cart subtotal");
        }

        if (cart.getDiscountAmount().compareTo(order.getDiscountTotal()) != 0) {
            log.error("Discount mismatch - cart: {}, order: {}", cart.getDiscountAmount(), order.getDiscountTotal());
            throw new IllegalStateException("Order discount does not match cart discount");
        }

        if (cart.getTotal().compareTo(order.getTotal()) != 0) {
            log.error("Total mismatch - cart: {}, order: {}", cart.getTotal(), order.getTotal());
            throw new IllegalStateException("Order total does not match cart total");
        }

        if (order.getShippingCost().compareTo(BigDecimal.ZERO) != 0) {
            log.error("POS order has non-zero shipping cost: {}", order.getShippingCost());
            throw new IllegalStateException("POS orders must have zero shipping cost");
        }

        if (order.getShipmentId() != null) {
            log.error("POS order has shipment assignment");
            throw new IllegalStateException("POS orders must not have shipment assignment");
        }

        if (!OrderEntity.OrderStatus.PAID.equals(order.getStatus())) {
            log.error("POS order status is not PAID: {}", order.getStatus());
            throw new IllegalStateException("POS orders must have PAID status");
        }

        if (!OrderEntity.PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            log.error("POS order payment status is not PAID: {}", order.getPaymentStatus());
            throw new IllegalStateException("POS orders must have PAID payment status");
        }

        log.info("Totals integrity validated - subtotal: {}, discount: {}, total: {}, shipping: 0",
                order.getSubtotal(), order.getDiscountTotal(), order.getTotal());
    }

    /**
     * Generate unique POS order number with format POS-YYYYMMDD-XXXX
     * Uses timestamp-based sequence for thread-safe uniqueness
     */
    private String generatePosOrderNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long sequence = System.currentTimeMillis() % 10000;
        String orderNumber = String.format("POS-%s-%04d", datePrefix, sequence);

        while (orderRepository.existsByOrderNumber(orderNumber)) {
            sequence = (sequence + 1) % 10000;
            orderNumber = String.format("POS-%s-%04d", datePrefix, sequence);
        }

        log.debug("Generated POS order number: {}", orderNumber);
        return orderNumber;
    }

    /**
     * Generate and store receipt for POS order
     * Returns the generated receipt number
     */
    private String generateAndStoreReceipt(OrderEntity order) {
        log.debug("Generating receipt for order: {}", order.getId());

        String receiptNumber = generateReceiptNumber();

        PosReceiptEntity receipt = PosReceiptEntity.builder()
                .orderId(order.getId())
                .receiptNumber(receiptNumber)
                .printCount(1)
                .printedAt(LocalDateTime.now())
                .build();

        receiptRepository.save(receipt);

        log.info("Receipt generated and stored - receiptNumber: {}, orderId: {}", receiptNumber, order.getId());
        return receiptNumber;
    }

    /**
     * Generate unique receipt number with format RCP-YYYYMMDD-XXXX
     * Sequential numbering per day (resets daily)
     * Uses database count for uniqueness
     */
    private String generateReceiptNumber() {
        LocalDate today = LocalDate.now();
        String datePrefix = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        LocalDateTime startOfDay = today.atStartOfDay();
        long dailyCount = receiptRepository.countReceiptsToday(startOfDay);

        long sequence = dailyCount + 1;
        String receiptNumber = String.format("RCP-%s-%04d", datePrefix, sequence);

        while (receiptRepository.existsByReceiptNumber(receiptNumber)) {
            sequence++;
            receiptNumber = String.format("RCP-%s-%04d", datePrefix, sequence);
        }

        log.debug("Generated receipt number: {} (daily sequence: {})", receiptNumber, sequence);
        return receiptNumber;
    }

    /**
     * Publish inventory deduction event asynchronously (fire-and-forget)
     * Event is sent to inventory module for async stock deduction
     */
    private void publishInventoryDeductionEventAsync(OrderEntity order) {
        try {
            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());

            List<Map<String, Object>> eventItems = items.stream()
                    .map(i -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("productId", i.getProductId());
                        item.put("variantId", i.getVariantId());
                        item.put("quantity", i.getQuantity());
                        return item;
                    })
                   .toList();

            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "POS_ORDER_PLACED");
            event.put("orderId", order.getId());
            event.put("orderUuid", order.getUuid());
            event.put("timestamp", Instant.now().toString());
            event.put("items", eventItems);

            rabbitTemplate.convertAndSend("pos.inventory.exchange", "pos.order.placed", event);
            log.info("Published POS_ORDER_PLACED event for order {} (items={})", order.getUuid(), eventItems.size());
        } catch (Exception e) {
            log.error("Failed to publish inventory deduction event for order {}: {}", order.getUuid(), e.getMessage(), e);
        }
    }
}
