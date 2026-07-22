package org.psint.beyosclothing.modules.delivery.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for Active Courier Lookup Requests
 * Handles requests from other modules to get the active (isActive=true) courier's API key
 * Response is sent back via RabbitMQ reply queue
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActiveCourierLookupConsumer {

    private final CourierRepository courierRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Listen for active courier lookup requests
     * Request: { "requestType": "GET_ACTIVE_COURIER" }
     * Response: { "success": true, "id": 1, "code": "KOOMBIYO", "name": "Koombiyo", "apiKey": "xxx", "apiBaseUrl": "https://...", "error": null }
     */
    @RabbitListener(queues = "active.courier.lookup.request", ackMode = "AUTO")
    @Transactional("deliveryTransactionManager")
    public void handleActiveCourierLookup(
            Message message,
            @Header(name = "amqp_replyTo", required = false) String replyTo,
            @Header(name = "amqp_correlationId", required = false) String correlationId
    ) {
        log.info("=== ACTIVE COURIER LOOKUP CONSUMER TRIGGERED ===");
        Map<String, Object> request;
        try {
            request = objectMapper.readValue(message.getBody(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize active courier lookup request", e);
            return;
        }
        log.info("ReplyTo: {}, CorrelationId: {}", replyTo, correlationId);

        try {
            String requestType = (String) request.get("requestType");
            log.info("Request type: {}", requestType);

            if ("GET_ACTIVE_COURIER".equals(requestType)) {
                Optional<Courier> activeCourier = courierRepository.findByIsActiveTrue();

                if (activeCourier.isPresent()) {
                    Courier courier = activeCourier.get();
                    log.info("✅ Found active courier: {} (ID: {})", courier.getCode(), courier.getId());

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("id", courier.getId());
                    response.put("code", courier.getCode());
                    response.put("name", courier.getName());
                    response.put("apiKey", courier.getApiKey());
                    response.put("apiBaseUrl", courier.getApiBaseUrl());
                    response.put("contactPhone", courier.getContactPhone());
                    response.put("email", courier.getEmail());
                    response.put("error", null);

                    if (replyTo != null) {
                        sendJsonResponse(replyTo, response, correlationId);
                        log.info("✅ Sent active courier response to: {} with correlationId: {}", replyTo, correlationId);
                    } else {
                        log.warn("⚠️ No replyTo address provided, cannot send response");
                    }
                } else {
                    log.warn("⚠️ No active courier found");
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", "No active courier found");

                    if (replyTo != null) {
                        sendJsonResponse(replyTo, response, correlationId);
                        log.info("✅ Sent error response to: {}", replyTo);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error in active courier lookup consumer: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            if (replyTo != null) {
                sendJsonResponse(replyTo, errorResponse, correlationId);
                log.info("✅ Sent exception response to: {}", replyTo);
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
            rabbitTemplate.convertAndSend(replyTo, responseMessage);
        } catch (Exception e) {
            log.error("Error converting response to JSON message for replyTo: {}", replyTo, e);
        }
    }
}
