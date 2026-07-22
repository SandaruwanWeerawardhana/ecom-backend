package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for handling pickup requested events from delivery module
 * ONLY handles order-level updates (pickup ID storage)
 * Does NOT access delivery module entities or services
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentPickupRequestedConsumer {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderRepository orderRepository;
    private final OrderSmsNotificationService smsNotificationService;


    /**
     * Listen for shipment pickup requested events from delivery module
     * Event payload: {
     *   "shipmentUuid": "xxx",
     *   "orderId": "yyy",
     *   "wayBillId": "yyy",
     *   "courierName": "Koombiyo",
     *   "pickupAddress": "address",
     *   "pickupId": 1520299,
     *   "courierResponse": {...},
     *   "timestamp": 1711269002000
     * }
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.shipment-pickup-requested:shipment.pickup.requested.order}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public void handlePickupRequested(Map<String, Object> event) {
        log.info("📮 Received shipment pickup requested event: {}", event);

        try {
            Long orderId = null;
            Object orderIdObj = event.get("orderId");
            if (orderIdObj instanceof Number) {
                orderId = ((Number) orderIdObj).longValue();
            }

            Long pickupId = null;
            Object pickupIdObj = event.get("pickupId");
            if (pickupIdObj instanceof Number) {
                pickupId = ((Number) pickupIdObj).longValue();
            }

            if (orderId == null) {
                log.warn("⚠️ Missing orderId in pickup requested event");
                return;
            }

            // Find and update order with pickup ID
            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);

            if (orderOpt.isPresent()) {
                OrderEntity order = orderOpt.get();
                // Get the last status history record to find the current status
                OrderStatusHistoryEntity lastHistory = orderStatusHistoryRepository.findTopByOrderIdOrderByIdDesc(orderId);
                String oldStatus = lastHistory != null ? lastHistory.getNewStatus() : order.getStatus().toString();

                if (pickupId != null) {
                    order.setPickupId(pickupId);
                    order.setStatus(OrderEntity.OrderStatus.OUT_FOR_DELIVERY);
                    orderRepository.save(order);

                    OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                            .orderId(order.getId())
                            .oldStatus(oldStatus)
                            .newStatus("OUT_FOR_DELIVERY")
                            .createdAt(LocalDateTime.now())
                            .changedBy(null)
                            .build();
                    orderStatusHistoryRepository.save(history);
                    log.info("✅ Order {} updated with pickupId: {} (Shipment: {})",
                            order.getOrderNumber(), pickupId, event.get("shipmentUuid"));

                    // Notify the order's owner (customer / POS customer / reseller) of the status change
                    smsNotificationService.sendOrderStatusUpdateForOrder(
                            order.getUuid(), OrderEntity.OrderStatus.OUT_FOR_DELIVERY.name());
                } else {
                    log.warn("⚠️ No pickupId found in pickup requested event for order: {}", order.getOrderNumber());
                }
            } else {
                log.warn("⚠️ Order not found with ID: {}", orderId);
            }

        } catch (Exception e) {
            log.error("❌ Error handling pickup requested event: {}", e.getMessage(), e);
        }
    }
}
