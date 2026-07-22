package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderShippingAddressEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderShippingAddressRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles courier delivery completion events for order, payment, reseller, and customer notification updates.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CourierDeliveryCompletedConsumer {

    private final OrderRepository orderRepository;
    private final OrderShippingAddressRepository shippingAddressRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OrderSmsNotificationService smsNotificationService;

    @Value("${app.rabbitmq.exchange.reseller:beyos.exchange.reseller}")
    private String resellerExchange;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    @RabbitListener(queues = "${app.rabbitmq.queue.order-delivery-completed:order.delivery.completed}")
    @Transactional("orderTransactionManager")
    public void handleDeliveryCompleted(Map<String, Object> event) {
        try {
            log.info("Received delivery completed event: {}", event);

            Long orderId = getLong(event.get("orderId"));
            if (orderId == null) {
                log.warn("Missing orderId in delivery completed event");
                return;
            }

            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found with ID: {}", orderId);
                return;
            }

            OrderEntity order = orderOpt.get();
            boolean completedNow = markOrderCompleted(order, event);

            if (isCustomerOrder(order) && completedNow) {
                sendCustomerCompletionSms(order);
            }

            if (order.getResellerId() != null) {
                log.info("Processing wallet credit for reseller order. orderId={}, resellerId={}, cartId={}",
                        orderId, order.getResellerId(), order.getCartId());
                publishResellerCartLookupEvent(order, event);
            }

            publishPaymentTransactionUpdateEvent(order, event);
        } catch (Exception e) {
            log.error("Error handling delivery completed event: {}", e.getMessage(), e);
        }
    }

    private boolean markOrderCompleted(OrderEntity order, Map<String, Object> event) {
        OrderEntity.OrderStatus previousStatus = order.getStatus();
        if (previousStatus == OrderEntity.OrderStatus.COMPLETED) {
            log.info("Order {} is already completed; skipping duplicate completion update", order.getId());
            return false;
        }

        order.setStatus(OrderEntity.OrderStatus.COMPLETED);
        orderRepository.save(order);

        OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                .orderId(order.getId())
                .oldStatus(previousStatus != null ? previousStatus.name() : null)
                .newStatus(OrderEntity.OrderStatus.COMPLETED.name())
                .changedBy(null)
                .notes(buildCompletionHistoryNotes(event))
                .build();
        statusHistoryRepository.save(history);

        log.info("Order {} status updated to COMPLETED", order.getId());
        return true;
    }

    private void sendCustomerCompletionSms(OrderEntity order) {
        shippingAddressRepository.findByOrderId(order.getId())
                .map(OrderShippingAddressEntity::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .ifPresentOrElse(
                        phone -> smsNotificationService.sendOrderCompletion(
                                phone,
                                order.getOrderNumber(),
                                order.getTotal()
                        ),
                        () -> log.warn("Skipping completion SMS; no shipping phone for customer order {}",
                                order.getOrderNumber())
                );
    }

    private boolean isCustomerOrder(OrderEntity order) {
        return order.getCustomerId() != null && order.getResellerId() == null;
    }

    private String buildCompletionHistoryNotes(Map<String, Object> event) {
        String waybillId = getString(event.get("waybillId"));
        String shipmentUuid = getString(event.get("shipmentUuid"));
        if (waybillId == null && shipmentUuid == null) {
            return "Courier delivery completed";
        }
        return String.format("Courier delivery completed | Waybill: %s | Shipment: %s", waybillId, shipmentUuid);
    }

    private void publishResellerCartLookupEvent(OrderEntity order, Map<String, Object> deliveryEvent) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", order.getId());
            event.put("resellerId", order.getResellerId());
            event.put("cartId", order.getCartId());
            event.put("shipmentUuid", deliveryEvent.get("shipmentUuid"));
            event.put("waybillId", deliveryEvent.get("waybillId"));
            event.put("deliveredAt", deliveryEvent.get("deliveredAt"));
            event.put("eventSource", "COURIER_DELIVERY_CONFIRMATION");
            event.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(resellerExchange, "reseller.order.delivered.cart.lookup", event);
            log.info("Published reseller cart lookup event for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Error publishing reseller cart lookup event: {}", e.getMessage(), e);
        }
    }

    private void publishPaymentTransactionUpdateEvent(OrderEntity order, Map<String, Object> deliveryEvent) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", order.getId());
            event.put("orderUuid", order.getUuid());
            event.put("orderNumber", order.getOrderNumber());
            event.put("paymentStatus", "CAPTURED");
            event.put("paymentReference", order.getPaymentReference());
            event.put("shipmentUuid", deliveryEvent.get("shipmentUuid"));
            event.put("waybillId", deliveryEvent.get("waybillId"));
            event.put("deliveredAt", deliveryEvent.get("deliveredAt"));
            event.put("codAmount", deliveryEvent.get("codAmount"));
            event.put("deliveryCharge", deliveryEvent.get("deliveryCharge"));
            event.put("eventSource", "COURIER_DELIVERY_CONFIRMATION");
            event.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(paymentExchange, "payment.transaction.delivery.confirmed", event);
            log.info("Published payment transaction update event for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Error publishing payment transaction update event: {}", e.getMessage(), e);
        }
    }

    private Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getString(Object value) {
        return value != null ? value.toString() : null;
    }
}
