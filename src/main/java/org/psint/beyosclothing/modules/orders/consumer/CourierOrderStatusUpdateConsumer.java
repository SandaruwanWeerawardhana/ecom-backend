package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for order status updates from delivery/courier module
 * Listens for status update events and records them in OrderStatusHistoryEntity
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CourierOrderStatusUpdateConsumer {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSmsNotificationService smsNotificationService;

    /**
     * Handle order status update event from courier tracking
     * Event format: {
     *   orderId: Long,
     *   oldStatus: String,
     *   newStatus: String,
     *   shipmentUuid: String,
     *   waybillId: String,
     *   courierStatus: String,
     *   courierData: Map,
     *   eventSource: String,
     *   timestamp: Long
     * }
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.order-status-updated-courier:order.status.updated.courier}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public void handleOrderStatusUpdate(Map<String, Object> event) {
        try {
            log.info("🔔 Received order status update event from courier: {}", event);

            Long orderId = null;
            Object orderIdObj = event.get("orderId");
            if (orderIdObj instanceof Number) {
                orderId = ((Number) orderIdObj).longValue();
            }

            if (orderId == null) {
                log.warn("⚠️ Missing orderId in courier status update event");
                return;
            }

            String newStatus = (String) event.get("newStatus");
            String oldStatus = (String) event.get("oldStatus");
            String shipmentUuid = (String) event.get("shipmentUuid");
            String courierStatus = (String) event.get("courierStatus");

            log.info("📦 Processing order status update: OrderId={}, Status={} -> {}, Courier Status={}",
                    orderId, oldStatus, newStatus, courierStatus);

            // Fetch order
            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);

            if (orderOpt.isEmpty()) {
                log.warn("⚠️ Order not found with ID: {}", orderId);
                return;
            }

            OrderEntity order = orderOpt.get();

            // Map string status to OrderStatus enum
            OrderEntity.OrderStatus mappedStatus = mapStringToOrderStatus(newStatus);

            // Check if status has already changed to this value
            if (order.getStatus() == mappedStatus) {
                log.debug("Order already has status: {}, skipping", mappedStatus);
                return;
            }

            // Record the status change in history
            OrderStatusHistoryEntity historyEntry = OrderStatusHistoryEntity.builder()
                    .orderId(orderId)
                    .oldStatus(oldStatus)
                    .newStatus(newStatus)
                    .changedBy(null) // System-initiated change
                    .notes("Courier Status: " + courierStatus + " | Shipment: " + shipmentUuid)
                    .build();

            orderStatusHistoryRepository.save(historyEntry);
            log.info("✅ Recorded order status history: {} -> {}", oldStatus, newStatus);

            // Update order status
            order.setStatus(mappedStatus);
            orderRepository.save(order);
            log.info("✅ Updated order {} status to {}", orderId, mappedStatus);

            // Notify the order's owner (customer / POS customer / reseller) of the status change
            smsNotificationService.sendOrderStatusUpdateForOrder(order.getUuid(), mappedStatus.name());

        } catch (Exception e) {
            log.error("❌ Error handling order status update from courier: {}", e.getMessage(), e);
        }
    }

    /**
     * Map string status to OrderStatus enum
     */
    private OrderEntity.OrderStatus mapStringToOrderStatus(String status) {
        if (status == null) {
            return OrderEntity.OrderStatus.COURIER_ORDER_PLACED;
        }

        try {
            return OrderEntity.OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown order status: {}, defaulting to COURIER_ORDER_PLACED", status);
            return OrderEntity.OrderStatus.COURIER_ORDER_PLACED;
        }
    }
}

