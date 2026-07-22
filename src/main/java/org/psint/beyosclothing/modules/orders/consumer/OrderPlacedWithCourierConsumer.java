package org.psint.beyosclothing.modules.orders.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for updating order status when placed with courier
 * Listens for events from admin order placement to update order status to PROCESSING
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderPlacedWithCourierConsumer {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSmsNotificationService smsNotificationService;
//    private final ObjectMapper objectMapper;

    /**
     * Listen for order placed with courier events
     * Request: { "orderUuid": "xxx", "wayBillId": "yyy", "courierName": "Koombiyo" }
     */
    @RabbitListener(queues = "order.placed.with.courier", ackMode = "AUTO")
    @Transactional("orderTransactionManager")
    public void handleOrderPlacedWithCourier(@Payload Map<String, Object> event) {
        log.info("=== ORDER PLACED WITH COURIER CONSUMER TRIGGERED ===");

        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            String orderUuid = (String) event.get("orderUuid");
            String wayBillId = (String) event.get("wayBillId");
            String courierName = (String) event.get("courierName");

            log.info("Processing order {} with waybill {} from courier {}", orderUuid, wayBillId, courierName);

            Optional<OrderEntity> orderOpt = orderRepository.findByUuid(orderUuid);

            if (orderOpt.isPresent()) {
                OrderEntity order = orderOpt.get();
                String oldStatus = order.getStatus().toString();

                // Update order status to PROCESSING
                order.setStatus(OrderEntity.OrderStatus.COURIER_ORDER_PLACED);
                order.setWayBillId(wayBillId);
                order.setUpdatedAt(LocalDateTime.now());

                OrderEntity updatedOrder = orderRepository.save(order);
                log.info("✅ Order {} status updated to PROCESSING with waybill {}", orderUuid, wayBillId);

                // Log status change
                OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                        .orderId(order.getId())
                        .oldStatus(oldStatus)
                        .newStatus(OrderEntity.OrderStatus.COURIER_ORDER_PLACED.toString())
                        .changedBy(1L) // System user
                        .notes("Order placed with courier - " + courierName)
                        .createdAt(LocalDateTime.now())
                        .build();

                orderStatusHistoryRepository.save(history);
                log.info("✅ Order status history recorded");

                // Notify the order's owner (customer / POS customer / reseller) of the status change
                smsNotificationService.sendOrderStatusUpdateForOrder(
                        orderUuid, OrderEntity.OrderStatus.COURIER_ORDER_PLACED.name());

            } else {
                log.warn("⚠️ Order not found with UUID: {}", orderUuid);
            }
        } catch (Exception e) {
            log.error("❌ Error in order placed with courier consumer: {}", e.getMessage(), e);
        }
    }
}

