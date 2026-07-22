package org.psint.beyosclothing.modules.delivery.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.psint.beyosclothing.modules.delivery.service.ShipmentTrackingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for handling pickup requested events from courier API
 * Updates shipment status and publishes cross-module events
 * Keeps delivery module internal, publishes events to order module
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShipmentPickupRequestedEventConsumer {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingService shipmentTrackingService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    /**
     * Handle pickup requested event from courier API
     * Updates shipment tracking and publishes event to order module
     *
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
//    @RabbitListener(
//            queues = "${app.rabbitmq.queue.shipment-pickup-requested:shipment.pickup.requested}",
//            containerFactory = "deliveryRabbitListenerContainerFactory"
//    )
    @RabbitListener(
            queues = "${app.rabbitmq.queue.shipment-pickup-requested-delivery:shipment.pickup.requested.delivery}",
            containerFactory = "deliveryRabbitListenerContainerFactory"
    )
    @Transactional("deliveryTransactionManager")
    public void handlePickupRequested(Map<String, Object> event) {
        log.info("📮 Received shipment pickup requested event: {}", event);

        try {
            String shipmentUuid = (String) event.get("shipmentUuid");
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

            // Find shipment by UUID
            Optional<Shipment> shipmentOpt = shipmentRepository.findByUuid(shipmentUuid);

            if (shipmentOpt.isEmpty()) {
                log.warn("⚠️ Shipment not found with UUID: {}", shipmentUuid);
                return;
            }

            Shipment shipment = shipmentOpt.get();

            // Update shipment status to REQUESTED_PICK_UP
            shipment.setStatus(Shipment.ShipmentStatus.REQUESTED_PICK_UP);
            shipmentRepository.save(shipment);
            log.info("✅ Shipment {} status updated to REQUESTED_PICK_UP", shipmentUuid);

            // Record tracking event for pickup request
            shipmentTrackingService.recordPickupRequestEvent(shipment, event);
            log.info("✅ Tracking event recorded for pickup request");

            // Publish event to order module to update pickup ID
            publishPickupRequestedToOrderModule(shipment, orderId, pickupId, event);

        } catch (Exception e) {
            log.error("❌ Error handling pickup requested event: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish pickup requested event to order module
     * Order module only needs: orderId, pickupId, shipmentUuid
     */
    private void publishPickupRequestedToOrderModule(Shipment shipment, Long orderId, Long pickupId, Map<String, Object> courierEvent) {
        try {
            if (orderId == null) {
                log.warn("⚠️ No orderId in pickup requested event, skipping order module notification");
                return;
            }

            Map<String, Object> event = new HashMap<>();
            event.put("orderId", orderId);
            event.put("shipmentUuid", shipment.getUuid());
            event.put("pickupId", pickupId);
            event.put("wayBillId", shipment.getWayBillId());
            event.put("pickupAddress", courierEvent.get("pickupAddress"));
            event.put("courierName", courierEvent.get("courierName"));
            event.put("courierResponse", courierEvent.get("courierResponse"));
            event.put("eventSource", "DELIVERY_PICKUP_CONFIRMATION");
            event.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(orderExchange, "shipment.pickup.requested", event);
            log.info("✅ Published pickup requested event to order module for order: {}", orderId);

        } catch (Exception e) {
            log.error("❌ Error publishing pickup requested event to order module: {}", e.getMessage(), e);
        }
    }
}
