package org.psint.beyosclothing.modules.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.entity.ShipmentTrackingEvent;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentTrackingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service for managing Shipment Tracking Events
 * Tracks all status changes and milestones of shipments
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentTrackingService {

    private final ShipmentTrackingEventRepository trackingEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Record a shipment tracking event
     *
     * @param shipment The shipment entity
     * @param status The new status
     * @param description Description of the event
     * @param location Current location (optional)
     * @param eventSource Source of the event (COURIER, SYSTEM, MANUAL, PICKUP_REQUEST, etc.)
     * @param rawPayload The raw event payload (optional)
     */
    @Transactional("deliveryTransactionManager")
    public ShipmentTrackingEvent recordTrackingEvent(
            Shipment shipment,
            Shipment.ShipmentStatus status,
            String description,
            String location,
            String eventSource,
            String rawPayload) {

        return recordTrackingEvent(shipment, status, description, location, eventSource, rawPayload, LocalDateTime.now());
    }

    /**
     * Record a shipment tracking event with specific event time
     */
    @Transactional("deliveryTransactionManager")
    public ShipmentTrackingEvent recordTrackingEvent(
            Shipment shipment,
            Shipment.ShipmentStatus status,
            String description,
            String location,
            String eventSource,
            String rawPayload,
            LocalDateTime eventTime) {

        try {
            ShipmentTrackingEvent trackingEvent = ShipmentTrackingEvent.builder()
                    .shipment(shipment)
                    .status(status)
                    .description(description)
                    .location(location)
                    .eventSource(eventSource)
                    .rawPayload(rawPayload)
                    .eventTime(eventTime)
                    .build();

            ShipmentTrackingEvent savedEvent = trackingEventRepository.save(trackingEvent);

            log.info("✅ Tracking event recorded - Shipment: {}, Status: {}, Source: {}, Description: {}",
                    shipment.getUuid(), status, eventSource, description);

            return savedEvent;

        } catch (Exception e) {
            log.error("❌ Error recording tracking event for shipment {}: {}", shipment.getUuid(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Record a pickup request event
     */
    @Transactional("deliveryTransactionManager")
    public ShipmentTrackingEvent recordPickupRequestEvent(
            Shipment shipment,
            Map<String, Object> pickupEventData) {

        try {
            String description = "Pickup requested from courier";
            String location = (String) pickupEventData.get("pickupAddress");
            String rawPayload = null;
            try {
                rawPayload = objectMapper.writeValueAsString(pickupEventData);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Failed to serialize pickup event data: {}", e.getMessage());
            }

            return recordTrackingEvent(
                    shipment,
                    Shipment.ShipmentStatus.REQUESTED_PICK_UP,
                    description,
                    location,
                    "PICKUP_REQUEST",
                    rawPayload
            );

        } catch (Exception e) {
            log.error("❌ Error recording pickup request event for shipment {}: {}", shipment.getUuid(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Record a status update event from courier
     */
    @Transactional("deliveryTransactionManager")
    public ShipmentTrackingEvent recordStatusUpdateEvent(
            Shipment shipment,
            Shipment.ShipmentStatus status,
            String koombiyoStatus,
            String statusDescription,
            String location,
            Map<String, Object> courierEventData) {

        try {
            String description = statusDescription != null ? statusDescription : "Status updated to " + status;
            String rawPayload = null;
            if (courierEventData != null) {
                try {
                    rawPayload = objectMapper.writeValueAsString(courierEventData);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.warn("Failed to serialize courier event data: {}", e.getMessage());
                }
            }

            return recordTrackingEvent(
                    shipment,
                    status,
                    description,
                    location,
                    "COURIER",
                    rawPayload
            );

        } catch (Exception e) {
            log.error("❌ Error recording status update event for shipment {}: {}", shipment.getUuid(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the complete tracking history for a shipment
     */
    public java.util.List<ShipmentTrackingEvent> getTrackingHistory(Shipment shipment) {
        return trackingEventRepository.findByShipmentOrderByEventTimeDesc(shipment);
    }

    /**
     * Get the latest tracking event for a shipment
     */
    public java.util.Optional<ShipmentTrackingEvent> getLatestTrackingEvent(Shipment shipment) {
        return trackingEventRepository.findLatestEventByShipment(shipment);
    }

    /**
     * Get all tracking events of a specific status
     */
    public java.util.List<ShipmentTrackingEvent> getTrackingEventsByStatus(Shipment.ShipmentStatus status) {
        return trackingEventRepository.findByStatus(status);
    }

    /**
     * Get tracking events from a specific source
     */
    public java.util.List<ShipmentTrackingEvent> getTrackingEventsBySource(String eventSource) {
        return trackingEventRepository.findByEventSource(eventSource);
    }
}
