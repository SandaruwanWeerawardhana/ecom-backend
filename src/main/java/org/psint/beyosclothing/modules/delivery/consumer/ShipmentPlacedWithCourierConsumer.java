package org.psint.beyosclothing.modules.delivery.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.entity.ShipmentTrackingEvent;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentTrackingEventRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for updating shipment status when placed with courier
 * Listens for events from admin order placement to update shipment status to BOOKED
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentPlacedWithCourierConsumer {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository shipmentTrackingEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Listen for shipment placed with courier events
     * Request: { "shipmentUuid": "xxx", "wayBillId": "yyy", "courierName": "Koombiyo", "courierResponse": {...} }
     */
    @RabbitListener(queues = "shipment.placed.with.courier", ackMode = "AUTO")
    @Transactional("deliveryTransactionManager")
    public void handleShipmentPlacedWithCourier(@Payload Map<String, Object> event) {
        log.info("=== SHIPMENT PLACED WITH COURIER CONSUMER TRIGGERED ===");

        try {
//            ObjectMapper objectMapper1 = new ObjectMapper();
//            Map<String, Object> event = objectMapper1.readValue(payload, Map.class);
            String shipmentUuid = (String) event.get("shipmentUuid");
            String wayBillId = (String) event.get("wayBillId");
            String courierName = (String) event.get("courierName");
            Map<String, Object> courierResponse = (Map<String, Object>) event.get("courierResponse");

            log.info("Processing shipment {} with waybill {} from courier {}", shipmentUuid, wayBillId, courierName);

            Optional<Shipment> shipmentOpt = shipmentRepository.findByUuid(shipmentUuid);

            if (shipmentOpt.isPresent()) {
                Shipment shipment = shipmentOpt.get();

                // Update shipment status to BOOKED
                shipment.setStatus(Shipment.ShipmentStatus.BOOKED);
                shipment.setWayBillId(wayBillId);
                shipment.setTrackingNumber(wayBillId);
                shipment.setBookedAt(LocalDateTime.now());

                // Set expected delivery date (3-5 days from now, default 4 days)
                LocalDateTime expectedDelivery = LocalDateTime.now().plus(4, ChronoUnit.DAYS);
                shipment.setDeliveredAt(expectedDelivery);

                Shipment updatedShipment = shipmentRepository.save(shipment);
                log.info("✅ Shipment {} status updated to BOOKED with tracking {} and expected delivery {}",
                        shipmentUuid, wayBillId, expectedDelivery);

                // Log tracking event with properly serialized JSON payload
                String rawPayloadJson = null;
                if (courierResponse != null) {
                    try {
                        rawPayloadJson = objectMapper.writeValueAsString(courierResponse);
                    } catch (Exception e) {
                        log.warn("Failed to serialize courier response to JSON: {}", e.getMessage());
                        rawPayloadJson = "{}";
                    }
                }

                ShipmentTrackingEvent trackingEvent = ShipmentTrackingEvent.builder()
                        .shipment(shipment)
                        .status(Shipment.ShipmentStatus.BOOKED)
                        .location("Courier - " + courierName)
                        .description("Order placed with courier service. Waybill: " + wayBillId)
                        .rawPayload(rawPayloadJson)
                        .eventTime(LocalDateTime.now())
                        .build();

                shipmentTrackingEventRepository.save(trackingEvent);
                log.info("✅ Shipment tracking event recorded");

            } else {
                log.warn("⚠️ Shipment not found with UUID: {}", shipmentUuid);
            }
        } catch (Exception e) {
            log.error("❌ Error in shipment placed with courier consumer: {}", e.getMessage(), e);
        }
    }
}
