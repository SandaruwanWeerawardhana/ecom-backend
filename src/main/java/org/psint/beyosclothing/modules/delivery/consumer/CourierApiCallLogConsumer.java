package org.psint.beyosclothing.modules.delivery.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierApiLog;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierApiLogRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Consumer for Courier API Call Log Events from Order Module
 * Handles asynchronous logging of courier API calls (HTTP requests/responses)
 *
 * ARCHITECTURE NOTE:
 * - Receives Map-based events from Order module (no DTOs)
 * - Persists API call logs to courier_api_logs table
 * - Used for audit trail and debugging courier integrations
 * - Non-blocking async event (doesn't fail main operation if logging fails)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CourierApiCallLogConsumer {

    private final CourierApiLogRepository courierApiLogRepository;
    private final CourierRepository courierRepository;

    /**
     * Handle Courier API Call Log Event from Order Module
     *
     * Event Map Keys:
     * - courierId: Long
     * - endpoint: String (API endpoint URL)
     * - requestPayload: String (JSON or form-encoded request body)
     * - responsePayload: String (API response body or error message)
     * - httpStatus: Integer (HTTP status code, 0 for errors)
     * - timestamp: LocalDateTime
     */
    @RabbitListener(queues = "courier.api.call.log", ackMode = "AUTO")
    @Transactional("deliveryTransactionManager")
    public void handleCourierApiCallLog(Map<String, Object> event) {
        log.info("=== COURIER API CALL LOG CONSUMER TRIGGERED ===");

        try {
            Long courierId = extractLong(event, "courierId");
            String endpoint = (String) event.get("endpoint");
            String requestPayload = (String) event.get("requestPayload");
            String responsePayload = (String) event.get("responsePayload");
            Integer httpStatus = extractInteger(event, "httpStatus");

            log.info("Logging courier API call - Courier ID: {}, Endpoint: {}, Status: {}",
                    courierId, endpoint, httpStatus);

            // Find courier if courierId is provided
            Courier courier = null;
            if (courierId != null) {
                Optional<Courier> courierOptional = courierRepository.findById(courierId);
                if (courierOptional.isPresent()) {
                    courier = courierOptional.get();
                    log.debug("Found courier: {} (ID: {})", courier.getName(), courierId);
                } else {
                    log.warn("Courier not found with ID: {}", courierId);
                }
            }

            // Create and save API log entry
            CourierApiLog apiLog = CourierApiLog.builder()
                    .courier(courier)
                    .endpoint(endpoint)
                    .requestPayload(requestPayload)
                    .responsePayload(responsePayload)
                    .httpStatus(httpStatus)
                    .build();

            CourierApiLog savedLog = courierApiLogRepository.save(apiLog);

            log.info("✅ Courier API call logged successfully - Log ID: {}, Courier: {}, Status: {}",
                    savedLog.getId(), courier != null ? courier.getName() : "N/A", httpStatus);

        } catch (Exception e) {
            // Log error but don't throw - this is async logging and shouldn't fail anything
            log.error("❌ Error logging courier API call: {}", e.getMessage(), e);
        }
    }

    /**
     * Extract Long value from event map safely
     */
    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse {} as Long: {}", key, value);
            return null;
        }
    }

    /**
     * Extract Integer value from event map safely
     */
    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse {} as Integer: {}", key, value);
            return null;
        }
    }
}

