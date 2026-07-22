package org.psint.beyosclothing.modules.delivery.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.psint.beyosclothing.modules.delivery.service.ShipmentTrackingService;
import org.psint.beyosclothing.modules.delivery.util.KoombiyoStatusMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for Shipment Status Update Events from Courier Service
 * Handles webhook/callback events from Koombiyo when shipment status changes
 *
 * ARCHITECTURE NOTE:
 * - Receives Map-based events with shipment tracking and new status
 * - Updates shipment status in database
 * - Maps Koombiyo status strings to ShipmentStatus enum
 * - Updates delivery timestamps based on status
 * - Records tracking events for every status change
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShipmentStatusUpdateConsumer {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingService shipmentTrackingService;

    /**
     * Handle Shipment Status Update Event from Courier Webhook
     *
     * Event Map Keys:
     * - trackingNumber: String (Koombiyo tracking number)
     * - wayBillId: String (Order waybill ID)
     * - koombiyoStatus: String (Status from Koombiyo API)
     * - statusDescription: String (Description of status)
     * - timestamp: LocalDateTime (When status was updated at courier)
     * - location: String (Current location)
     */
    @RabbitListener(queues = "shipment.status.update", ackMode = "AUTO")
    @Transactional("deliveryTransactionManager")
    public void handleShipmentStatusUpdate(Map<String, Object> event) {
        log.info("=== SHIPMENT STATUS UPDATE CONSUMER TRIGGERED ===");

        try {
            String trackingNumber = (String) event.get("trackingNumber");
            String wayBillId = (String) event.get("wayBillId");
            String koombiyoStatus = (String) event.get("koombiyoStatus");
            String statusDescription = (String) event.get("statusDescription");
            String location = (String) event.get("location");

            log.info("Status update - Tracking: {}, WayBill: {}, Koombiyo Status: {}, Location: {}",
                    trackingNumber, wayBillId, koombiyoStatus, location);

            // Validate required fields
            if (trackingNumber == null || trackingNumber.isBlank()) {
                log.warn("Missing trackingNumber in status update event");
                return;
            }

            if (koombiyoStatus == null || koombiyoStatus.isBlank()) {
                log.warn("Missing koombiyoStatus in status update event for tracking: {}", trackingNumber);
                return;
            }

            // Map Koombiyo status to ShipmentStatus enum
            Shipment.ShipmentStatus mappedStatus = KoombiyoStatusMapper.mapKoombiyoStatus(koombiyoStatus);

            if (mappedStatus == null) {
                log.error("Failed to map Koombiyo status '{}' for tracking: {}. Status update skipped.",
                        koombiyoStatus, trackingNumber);
                return;
            }

            // Find shipment by tracking number
            Optional<Shipment> shipmentOptional = shipmentRepository.findByTrackingNumber(trackingNumber);

            if (shipmentOptional.isEmpty()) {
                log.warn("Shipment not found with tracking number: {}", trackingNumber);
                return;
            }

            Shipment shipment = shipmentOptional.get();
            Shipment.ShipmentStatus previousStatus = shipment.getStatus();

            // Update shipment status
            shipment.setStatus(mappedStatus);

            // Update timestamps based on status
            LocalDateTime now = LocalDateTime.now();

            if (KoombiyoStatusMapper.isDelivered(mappedStatus)) {
                shipment.setDeliveredAt(now);
                log.info("✅ Shipment delivered - Tracking: {}, Previous Status: {}", trackingNumber, previousStatus);
            } else if (KoombiyoStatusMapper.isInTransit(mappedStatus)) {
                if (shipment.getShippedAt() == null) {
                    shipment.setShippedAt(now);
                    log.info("✅ Shipment shipped - Tracking: {}", trackingNumber);
                }
            } else if (KoombiyoStatusMapper.isFinalFailed(mappedStatus)) {
                log.warn("⚠️ Shipment failed/returned - Tracking: {}, Status: {}", trackingNumber, mappedStatus);
            }

            // Save updated shipment
            shipmentRepository.save(shipment);

            // Record tracking event for status change
            shipmentTrackingService.recordStatusUpdateEvent(
                    shipment,
                    mappedStatus,
                    koombiyoStatus,
                    statusDescription,
                    location,
                    event
            );

            log.info("✅ Shipment status updated - Tracking: {}, Previous: {}, Current: {}, Description: {}",
                    trackingNumber, previousStatus, mappedStatus, statusDescription);

        } catch (Exception e) {
            log.error("❌ Error processing shipment status update: {}", e.getMessage(), e);
            // Don't re-throw - let the message be acknowledged even if processing fails
        }
    }
}
