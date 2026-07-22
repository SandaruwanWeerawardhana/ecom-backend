package org.psint.beyosclothing.modules.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSmsNotificationService {

    private final EsmsService esmsService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PosCustomerRepository posCustomerRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.reseller:beyos.exchange.reseller}")
    private String resellerExchange;

    @Async
    public void sendOrderConfirmation(String customerPhone, String orderNumber, BigDecimal total, String status) {
        String message = String.format(
                "Your Beyos order %s has been placed successfully. Status: %s. Total: LKR %.2f. Thank you for shopping with us!",
                orderNumber, status, total
        );
        sendOrderSms(customerPhone, orderNumber, message);
    }

    @Async
    public void sendOrderConfirmationForOrder(String orderUuid, BigDecimal total, String fallbackStatus) {
        if (!hasText(orderUuid)) {
            log.warn("Skipping order confirmation SMS - order UUID is missing");
            return;
        }

        Optional<OrderEntity> orderOptional = findOrderWithRetry(orderUuid);
        if (orderOptional.isEmpty()) {
            log.warn("Skipping order confirmation SMS - order not found for UUID {}", orderUuid);
            return;
        }

        OrderEntity order = orderOptional.get();
        String customerPhone = resolveCustomerPhone(order);
        String orderNumber = order.getOrderNumber();
        String status = order.getStatus() != null ? order.getStatus().name() : fallbackStatus;
        BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : total;

        String message = String.format(
                "Your Beyos order %s has been placed successfully. Status: %s. Total: LKR %.2f. Thank you for shopping with us!",
                orderNumber, status, orderTotal
        );
        sendOrderSms(customerPhone, orderNumber, message);
    }

    /**
     * Sends a status-update SMS to the order's owner, resolving the recipient (customer, POS
     * customer or reseller) from the order itself. Use this from any flow that changes an order's
     * status. The order is (re)loaded here so it is safe to call after a transaction has committed.
     */
    @Async
    public void sendOrderStatusUpdateForOrder(String orderUuid, String status) {
        if (!hasText(orderUuid)) {
            log.warn("Skipping order status SMS - order UUID is missing");
            return;
        }

        Optional<OrderEntity> orderOptional = findOrderWithRetry(orderUuid);
        if (orderOptional.isEmpty()) {
            log.warn("Skipping order status SMS - order not found for UUID {}", orderUuid);
            return;
        }

        OrderEntity order = orderOptional.get();
        String recipientPhone = resolveRecipientPhone(order);
        if (!hasText(recipientPhone)) {
            log.warn("Skipping status SMS - could not resolve recipient phone for order {}", order.getOrderNumber());
            return;
        }

        String message = String.format(
                "Your Beyos order %s status has been updated to: %s.",
                order.getOrderNumber(), status
        );
        sendOrderSms(recipientPhone, order.getOrderNumber(), message);
    }

    /**
     * Resolves the phone number to notify for an order from its owner columns: when the order has no
     * customer it belongs to a reseller (phone read from the reseller record via RabbitMQ); otherwise
     * it is a customer order and the phone comes from the source-specific record - the online Customer
     * for ONLINE orders or the PosCustomer for POS orders.
     */
    public String resolveRecipientPhone(OrderEntity order) {
        if (order.getCustomerId() == null) {
            if (order.getResellerId() == null) {
                log.warn("Order {} has neither customerId nor resellerId - cannot resolve recipient phone",
                        order.getOrderNumber());
                return null;
            }
            return fetchResellerPhone(order.getResellerId());
        }
        return resolveCustomerPhone(order);
    }

    @SuppressWarnings("unchecked")
    private String fetchResellerPhone(Long resellerId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("resellerId", resellerId);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));
            Object response = rabbitTemplate.convertSendAndReceive(
                    resellerExchange,
                    "reseller.name.lookup.request",
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                if (Boolean.TRUE.equals(responseMap.get("found"))) {
                    return (String) responseMap.get("phone");
                }
            }

            log.warn("No reseller phone found for resellerId={}", resellerId);
        } catch (Exception e) {
            log.warn("Could not fetch reseller phone for resellerId={}: {}", resellerId, e.getMessage());
        }

        return null;
    }

    @Async
    public void sendOrderCompletion(String customerPhone, String orderNumber, BigDecimal total) {
        String message = String.format(
                "Your Beyos order %s has been delivered successfully. Total: LKR %.2f. Thank you for shopping with us!",
                orderNumber, total
        );
        sendOrderSms(customerPhone, orderNumber, message);
    }

    private Optional<OrderEntity> findOrderWithRetry(String orderUuid) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            Optional<OrderEntity> order = orderRepository.findByUuid(orderUuid);
            if (order.isPresent() || attempt == 3) {
                return order;
            }
            sleepBeforeRetry(orderUuid, attempt);
        }
        return Optional.empty();
    }

    private void sleepBeforeRetry(String orderUuid, int attempt) {
        try {
            Thread.sleep(150L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to resolve order {} for SMS", orderUuid);
        }
    }

    public String resolveCustomerPhone(OrderEntity order) {
        if (order.getCustomerId() == null) {
            log.warn("Skipping order SMS - no customer ID for order {}", order.getOrderNumber());
            return null;
        }

        if (order.getSource() == OrderEntity.OrderSource.POS) {
            return posCustomerRepository.findById(order.getCustomerId())
                    .map(PosCustomerEntity::getPhone)
                    .filter(this::hasText)
                    .orElseGet(() -> {
                        log.warn("Skipping order SMS - POS customer phone not found. order={}, customerId={}",
                                order.getOrderNumber(), order.getCustomerId());
                        return null;
                    });
        }

        return customerRepository.findById(order.getCustomerId())
                .map(Customer::getPhone)
                .filter(this::hasText)
                .orElseGet(() -> {
                    log.warn("Skipping order SMS - online customer phone not found. order={}, customerId={}",
                            order.getOrderNumber(), order.getCustomerId());
                    return null;
                });
    }

    private void sendOrderSms(String customerPhone, String orderNumber, String message) {
        if (customerPhone == null || customerPhone.isBlank()) {
            log.warn("Skipping order SMS - no phone number for order {}", orderNumber);
            return;
        }

        String transactionId = generateProviderTransactionId();

        try {
            EsmsSendResult result = esmsService.sendSms(List.of(customerPhone), message, transactionId);

            if (result.isSuccess()) {
                log.info("Order SMS sent for order {} - transactionId: {}, providerMessage: {}",
                        orderNumber, result.getTransactionId(), result.getProviderMessage());
            } else if ("NOT_CONFIGURED".equals(result.getErrorCode())) {
                log.info("Order SMS skipped for order {} because eSMS credentials are not configured", orderNumber);
            } else {
                log.warn("Order SMS failed for order {} (errCode={}): {}",
                        orderNumber, result.getErrorCode(), result.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("Unexpected error sending SMS for order {}: {}", orderNumber, e.getMessage());
        }
    }

    private String generateProviderTransactionId() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return System.currentTimeMillis() + String.valueOf(suffix);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
