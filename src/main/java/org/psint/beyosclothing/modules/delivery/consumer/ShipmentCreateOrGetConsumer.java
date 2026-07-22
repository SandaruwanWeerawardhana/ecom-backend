package org.psint.beyosclothing.modules.delivery.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumer for Create or Get Shipment Requests from Order Module
 * Handles RabbitMQ RPC calls using Map-based communication (no DTOs)
 *
 * ARCHITECTURE NOTE:
 * - Communication is purely via Map objects (no cross-module entity imports)
 * - Supports both creating new shipments and retrieving existing ones
 * - Used by Order module via AdminOrderCourierServiceImpl
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShipmentCreateOrGetConsumer {

    private final ShipmentRepository shipmentRepository;
    private final CourierRepository courierRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    //tracking url  base url
    private static final String TRACKING_URL_BASE = "https://koombiyodelivery.lk/Track/track_id";

    /**
     * Handle Create or Get Shipment Request from Order Module
     *
     * Request Map Keys:
     * - requestType: "CREATE_OR_GET_SHIPMENT"
     * - orderId: Long
     * - courierId: Long
     * - courierName: String
     * - shipmentWeight: BigDecimal
     * - shippingCost: BigDecimal
     *
     * Response Map Keys (on success):
     * - success: true
     * - shipmentUuid: String (UUID)
     * - shipmentId: Long
     * - status: String (Shipment status)
     * - orderId: Long
     * - courierId: Long
     *
     * Response Map Keys (on failure):
     * - success: false
     * - error: String (error message)
     */
    @RabbitListener(queues = "shipment.create.or.get.request", ackMode = "AUTO")
    @Transactional("deliveryTransactionManager")
    public void handleCreateOrGetShipment(
            Message message,
            @Header(name = "amqp_replyTo", required = false) String replyTo,
            @Header(name = "amqp_correlationId", required = false) String correlationId
    ) {
        log.info("=== SHIPMENT CREATE OR GET CONSUMER TRIGGERED ===");

        Map<String, Object> request;
        try {
            request = objectMapper.readValue(message.getBody(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize shipment create or get request", e);
            if (replyTo != null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Deserialization failed: " + e.getMessage());
                sendJsonResponse(replyTo, errorResponse, correlationId);
            }
            return;
        }

        try {
            String requestType = (String) request.get("requestType");
            log.info("Request type: {}", requestType);

            if (!"CREATE_OR_GET_SHIPMENT".equals(requestType)) {
                log.warn("Invalid request type: {}", requestType);
                Map<String, Object> response = buildErrorResponse("Invalid request type: " + requestType);
                if (replyTo != null) {
                    sendJsonResponse(replyTo, response, correlationId);
                }
                return;
            }

            Long orderId = extractLong(request, "orderId");
            Long courierId = extractLong(request, "courierId");
            String courierName = (String) request.get("courierName");
            String wayBillId = (String) request.get("wayBillId");
            String phone = (String) request.get("receiverPhone");

            BigDecimal shipmentWeight = extractBigDecimal(request, "shipmentWeight");
            BigDecimal shippingCost = extractBigDecimal(request, "shippingCost");

            log.info("Request: orderId={}, courierId={}, courierName={}, weight={}, cost={}",
                    orderId, courierId, courierName, shipmentWeight, shippingCost);

            // Validate required fields
            if (orderId == null || courierId == null) {
                String errorMsg = "Missing required fields: orderId=" + orderId + ", courierId=" + courierId;
                log.warn(errorMsg);
                Map<String, Object> response = buildErrorResponse(errorMsg);
                if (replyTo != null) {
                    sendJsonResponse(replyTo, response, correlationId);
                }
                return;
            }

            // Provide default weight if not specified (0 kg as placeholder)
            if (shipmentWeight == null) {
                shipmentWeight = BigDecimal.ZERO;
                log.info("Shipment weight not provided, using default: 0 kg");
            }

            // Provide default shipping cost if not specified
            if (shippingCost == null) {
                shippingCost = BigDecimal.ZERO;
                log.info("Shipping cost not provided, using default: 0");
            }

            // Try to get existing shipment for this order
            Optional<Shipment> existingShipment = shipmentRepository.findByOrderId(orderId);

            if (existingShipment.isPresent()) {
                Shipment shipment = existingShipment.get();
                log.info("✅ Found existing shipment for order {}: UUID={}, ID={}", orderId, shipment.getUuid(), shipment.getId());
                Map<String, Object> response = buildSuccessResponse(shipment);
                if (replyTo != null) {
                    sendJsonResponse(replyTo, response, correlationId);
                }
                return;
            }

            // Create new shipment if not exists
            log.info("Creating new shipment for order {}", orderId);

            // Validate courier exists
            Optional<Courier> courierOptional = courierRepository.findById(courierId);
            if (courierOptional.isEmpty()) {
                String errorMsg = "Courier not found with ID: " + courierId;
                log.warn(errorMsg);
                Map<String, Object> response = buildErrorResponse(errorMsg);
                if (replyTo != null) {
                    sendJsonResponse(replyTo, response, correlationId);
                }
                return;
            }

            Courier courier = courierOptional.get();

            // Create new shipment
            Shipment newShipment = Shipment.builder()
                    .uuid(UUID.randomUUID().toString())
                    .orderId(orderId)
                    .courier(courier)
                    .trackingNumber(wayBillId)
                    .wayBillId(wayBillId)
                    .trackingUrl(TRACKING_URL_BASE+"?id="+wayBillId+"&phone="+phone)
                    .shipmentWeight(shipmentWeight)
                    .shippingCost(shippingCost)
                    .status(Shipment.ShipmentStatus.PENDING)
                    .bookedAt(LocalDateTime.now())
                    .deliveredAt(LocalDateTime.now().plus(4, ChronoUnit.DAYS))
                    .payerType(Shipment.PayerType.RESELLER)
                    .build();

            newShipment = shipmentRepository.save(newShipment);

            log.info("✅ New shipment created: UUID={}, ID={}, Order ID={}",
                    newShipment.getUuid(), newShipment.getId(), orderId);

            Map<String, Object> response = buildSuccessResponse(newShipment);
            if (replyTo != null) {
                sendJsonResponse(replyTo, response, correlationId);
            }

        } catch (Exception e) {
            log.error("❌ Error in shipment create or get consumer: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            if (replyTo != null) {
                sendJsonResponse(replyTo, errorResponse, correlationId);
            }
        }
    }

    /**
     * Helper method to send JSON response via RabbitMQ
     * Converts Map to JSON bytes and sets proper content-type headers
     */
    private void sendJsonResponse(String replyTo, Map<String, Object> response, String correlationId) {
        try {
            byte[] jsonBody = objectMapper.writeValueAsBytes(response);
            MessageProperties props = new MessageProperties();
            props.setContentType("application/json");
            props.setContentEncoding("UTF-8");
            props.setContentLength(jsonBody.length);
            if (correlationId != null) {
                props.setCorrelationId(correlationId);
            }
            Message responseMessage = new Message(jsonBody, props);
            if (rabbitTemplate != null) {
                rabbitTemplate.convertAndSend(replyTo, responseMessage);
            }
        } catch (Exception e) {
            log.error("Error converting response to JSON message for replyTo: {}", replyTo, e);
        }
    }

    private Map<String, Object> buildSuccessResponse(Shipment shipment) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("shipmentUuid", shipment.getUuid());
        response.put("shipmentId", shipment.getId());
        response.put("status", shipment.getStatus().toString());
        response.put("orderId", shipment.getOrderId());
        response.put("courierId", shipment.getCourier().getId());
        response.put("trackingUrl", shipment.getTrackingUrl());
        return response;
    }

    private Map<String, Object> buildErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Could not parse Long from {}: {}", key, value);
            return null;
        }
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Could not parse BigDecimal from {}: {}", key, value);
            return null;
        }
    }
}
