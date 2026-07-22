package org.psint.beyosclothing.modules.delivery.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shipment Lookup Consumer
 * Handles RPC requests from the order module to fetch shipment/tracking info.
 * <p>
 * Request Type 1 - By Order ID (Legacy):
 * Keys: orderId
 * Response: found, carrier, trackingNumber, trackingUrl, status, bookedAt, shippedAt, deliveredAt, shippingCost
 * <p>
 * Request Type 2 - By Order ID for Pickup (NEW):
 * Keys: requestType="GET_SHIPMENT_FOR_PICKUP", orderId
 * Response: success, shipmentUuid, wayBillId, apiKey, apiBaseUrl, courierName, courierId
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentLookupConsumer {

    private final ShipmentRepository shipmentRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.shipment-lookup-request:shipment.lookup.request.queue}")
    @Transactional(readOnly = true)
    public Map<String, Object> handleShipmentLookup(@Payload Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.debug("[SHIPMENT LOOKUP] Received request: {}", request);
            // Check if this is a pickup request lookup
            String requestType = (String) request.get("requestType");

            if ("GET_SHIPMENT_FOR_PICKUP".equals(requestType)) {
                response = handleShipmentLookupForPickup(request);
            } else {
                response = handleShipmentLookupByOrderId(request);
            }

            // Return response as Map directly (RabbitMQ will serialize it)
            return response;

        } catch (Exception e) {
            log.error("[SHIPMENT LOOKUP] Error processing request", e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());

            // Return error response as Map
            return response;
        }
    }

    /**
     * Handle legacy shipment lookup by Order ID
     * Used for customer-facing shipment tracking queries
     */
    private Map<String, Object> handleShipmentLookupByOrderId(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        Long orderId = null;
        Object orderIdObj = request != null ? request.get("orderId") : null;
        if (orderIdObj instanceof Number) {
            orderId = ((Number) orderIdObj).longValue();
        }

        if (orderId == null) {
            log.warn("[SHIPMENT LOOKUP] Missing orderId in request");
            response.put("found", false);
            response.put("error", "orderId is required");
            return response;
        }

        var shipmentOptional = shipmentRepository.findByOrderId(orderId);

        if (shipmentOptional.isEmpty()) {
            log.debug("[SHIPMENT LOOKUP] No shipment found for orderId={}", orderId);
            response.put("found", false);
            return response;
        }

        Shipment shipment = shipmentOptional.get();

        response.put("found", true);
        response.put("carrier", shipment.getCourier() != null ? shipment.getCourier().getName() : null);
        response.put("trackingNumber", shipment.getTrackingNumber());
        response.put("trackingUrl", shipment.getTrackingUrl());
        response.put("status", shipment.getStatus() != null ? shipment.getStatus().name() : null);
        response.put("bookedAt", shipment.getBookedAt() != null ? shipment.getBookedAt().toString() : null);
        response.put("shippedAt", shipment.getShippedAt() != null ? shipment.getShippedAt().toString() : null);
        response.put("deliveredAt", shipment.getDeliveredAt() != null ? shipment.getDeliveredAt().toString() : null);
        response.put("shippingCost", shipment.getShippingCost());

        log.debug("[SHIPMENT LOOKUP] Found shipment for orderId={}, tracking={}", orderId, shipment.getTrackingNumber());
        return response;
    }

    /**
     * Handle shipment lookup by Order ID for pickup requests
     * Used by admin to get shipment details when adding pickup requests
     * Order module passes orderId, and we find the shipment by orderId
     * Returns courier API credentials needed for Koombiyo pickup API call
     */
    private Map<String, Object> handleShipmentLookupForPickup(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        Long orderId = null;
        Object orderIdObj = request.get("orderId");
        if (orderIdObj instanceof Number) {
            orderId = ((Number) orderIdObj).longValue();
        }

        if (orderId == null) {
            log.warn("[SHIPMENT LOOKUP FOR PICKUP] Missing orderId in request");
            response.put("success", false);
            response.put("error", "orderId is required");
            return response;
        }

        log.info("[SHIPMENT LOOKUP FOR PICKUP] Fetching shipment for Order ID: {}", orderId);

        // Find shipment by orderId
        Optional<Shipment> shipmentOptional = shipmentRepository.findByOrderId(orderId);

        if (shipmentOptional.isEmpty()) {
            log.warn("[SHIPMENT LOOKUP FOR PICKUP] No shipment found for Order ID: {}", orderId);
            response.put("success", false);
            response.put("error", "Shipment not found for order ID: " + orderId);
            return response;
        }

        Shipment shipment = shipmentOptional.get();

        // Validate courier is assigned
        if (shipment.getCourier() == null) {
            log.error("[SHIPMENT LOOKUP FOR PICKUP] Shipment has no courier assigned - Shipment ID: {}", shipment.getId());
            response.put("success", false);
            response.put("error", "Shipment has no courier assigned");
            return response;
        }

        // Validate courier has API credentials
        String apiKey = shipment.getCourier().getApiKey();
        String apiBaseUrl = shipment.getCourier().getApiBaseUrl();

        if (apiKey == null || apiKey.isBlank() || apiBaseUrl == null || apiBaseUrl.isBlank()) {
            log.error("[SHIPMENT LOOKUP FOR PICKUP] Courier missing API credentials - Courier: {}", shipment.getCourier().getName());
            response.put("success", false);
            response.put("error", "Courier API credentials not configured");
            return response;
        }

        // Build response with all required details for pickup request
        response.put("success", true);
        response.put("shipmentUuid", shipment.getUuid());
        response.put("wayBillId", shipment.getWayBillId());
        response.put("apiKey", apiKey);
        response.put("apiBaseUrl", apiBaseUrl);
        response.put("courierName", shipment.getCourier().getName());
        response.put("courierId", shipment.getCourier().getId());

        log.info("[SHIPMENT LOOKUP FOR PICKUP] ✅ Found shipment - UUID: {}, Waybill: {}, Courier: {}",
                shipment.getUuid(), shipment.getWayBillId(), shipment.getCourier().getName());

        return response;
    }
}
