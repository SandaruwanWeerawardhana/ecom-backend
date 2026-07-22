package org.psint.beyosclothing.modules.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierApiLog;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.repository.CourierApiLogRepository;
import org.psint.beyosclothing.modules.delivery.repository.CourierRepository;
import org.psint.beyosclothing.modules.delivery.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Service for polling Koombiyo Courier API for order/shipment tracking updates.
 * Fetches all active shipments and retrieves their tracking status from the courier.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CourierTrackingPollingService {

    private static final String TRACK_ORDERS_PATH = "Allorders/users";

    private final ShipmentRepository shipmentRepository;
    private final CourierRepository courierRepository;
    private final CourierApiLogRepository courierApiLogRepository;
    private final ObjectMapper objectMapper;
    private final CourierResponseProcessingService responseProcessingService;

    @Autowired
    @Qualifier("deliveryRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Poll Koombiyo API for tracking updates for all active shipments.
     * This method fetches all shipments with non-final statuses and calls the courier API
     * to get updated tracking information.
     *
     * @return Number of shipments polled
     */
    @Transactional("deliveryTransactionManager")
    public int pollActiveShipmentsTracking() {
        log.info("=== STARTING COURIER TRACKING POLLING ===");

        try {
            // Get the active Koombiyo courier
            Optional<Courier> activeCourrierOpt = courierRepository.findAllByIsActiveTrue()
                    .stream()
                    .findFirst();

            if (activeCourrierOpt.isEmpty()) {
                log.warn("No active courier found for tracking polling");
                return 0;
            }

            Courier courier = activeCourrierOpt.get();

            // Get all active (non-final) shipments
            // We consider a shipment active if it's not in a final state (DELIVERED, FAILED, RETURNED)
            List<Shipment> activeShipments = getActiveShipments();

            log.info("Found {} active shipments to track", activeShipments.size());

            if (activeShipments.isEmpty()) {
                log.info("No active shipments to poll");
                return 0;
            }

            int pollCount = 0;

            // Poll each shipment's tracking status
            for (Shipment shipment : activeShipments) {
                try {
                    pollShipmentTracking(courier, shipment);
                    pollCount++;
                } catch (Exception e) {
                    log.error("Error polling tracking for shipment {}: {}", shipment.getUuid(), e.getMessage());
                    // Continue with next shipment
                }
            }

            log.info("=== COURIER TRACKING POLLING COMPLETED - {} shipments polled ===", pollCount);
            return pollCount;

        } catch (Exception e) {
            log.error("Fatal error in courier tracking polling: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Poll tracking status for a single shipment from the courier API.
     *
     * @param courier The active courier
     * @param shipment The shipment to track
     */
    private void pollShipmentTracking(Courier courier, Shipment shipment) {
        if (shipment.getWayBillId() == null || shipment.getWayBillId().isBlank()) {
            log.warn("Shipment {} has no waybill ID, skipping tracking poll", shipment.getUuid());
            return;
        }

        String endpoint = buildEndpoint(courier, TRACK_ORDERS_PATH);
        String waybillId = shipment.getWayBillId();

        log.info("Polling tracking for shipment {} with waybill {}", shipment.getUuid(), waybillId);

        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("apikey", courier.getApiKey());
        formBody.add("waybillid", waybillId);
        formBody.add("offset", "0");
        formBody.add("limit", "1");

        String requestPayload = "apikey=***&waybillid=" + waybillId + "&offset=0&limit=1";
        String rawResponse = null;
        int httpStatus = 0;

        try {
            ResponseEntity<String> response = postForm(endpoint, formBody);
            httpStatus = response.getStatusCode().value();
            rawResponse = response.getBody();

            log.info("📦 Courier API Response [HTTP {}] for shipment {} (waybill {}):",
                    httpStatus, shipment.getUuid(), waybillId);
            log.info("📦 Raw Response Body:\n{}", rawResponse);

            // Log the response for analysis
            // Parse and process the response
            if (rawResponse != null && !rawResponse.isEmpty()) {
                java.util.Map<String, Object> parsedResponse = objectMapper.readValue(rawResponse, java.util.Map.class);
                responseProcessingService.processKoombiyoTrackingResponse(parsedResponse);
            }


        } catch (HttpStatusCodeException ex) {
            httpStatus = ex.getStatusCode().value();
            rawResponse = ex.getResponseBodyAsString();
            log.error("❌ Courier API error [HTTP {}] for shipment {} (waybill {}): {}",
                    httpStatus, shipment.getUuid(), waybillId, rawResponse);
        } catch (Exception ex) {
            httpStatus = 0;
            rawResponse = ex.getMessage();
            log.error("❌ Unexpected error polling tracking for shipment {} (waybill {}): {}",
                    shipment.getUuid(), waybillId, ex.getMessage(), ex);
        } finally {
            saveApiLog(courier, endpoint, requestPayload, rawResponse, httpStatus, shipment.getUuid());
        }
    }

    /**
     * Get all active shipments (not in final states).
     * Active states are: PENDING, BOOKED, PROCESSING, PICKED, ON_QC, IN_TRANSIT, COLLECTED_BY_KOOMBIYO,
     * DISPATCH_TO_DESTINATION, RECEIVED_AT_DESTINATION, OUT_FOR_DELIVERY, REQUESTED_PICK_UP, etc.
     *
     * @return List of active shipments
     */
    private List<Shipment> getActiveShipments() {
        // For now, we'll fetch shipments that are not in final states
        // Final states: DELIVERED, FAILED, RETURNED
        List<Shipment> allShipments = shipmentRepository.findAll();

        return allShipments.stream()
                .filter(s -> !isFinalStatus(s.getStatus()))
                .toList();
    }

    /**
     * Check if a shipment status is in a final/terminal state.
     *
     * @param status The shipment status
     * @return True if the status is final
     */
    private boolean isFinalStatus(Shipment.ShipmentStatus status) {
        return status == Shipment.ShipmentStatus.DELIVERED
                || status == Shipment.ShipmentStatus.FAILED
                || status == Shipment.ShipmentStatus.RETURNED
                || status == Shipment.ShipmentStatus.CLIENT_RECEIVED;
    }

    /**
     * Build the full endpoint URL from the courier's apiBaseUrl and the path suffix.
     */
    private String buildEndpoint(Courier courier, String path) {
        String base = (courier.getApiBaseUrl() != null && !courier.getApiBaseUrl().isBlank())
                ? courier.getApiBaseUrl()
                : "https://application.koombiyodelivery.lk/api/";

        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + path;
    }

    /**
     * POST application/x-www-form-urlencoded to the given endpoint.
     */
    private ResponseEntity<String> postForm(String url, MultiValueMap<String, String> formBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formBody, headers);
        return restTemplate.postForEntity(url, request, String.class);
    }

    /**
     * Log the tracking response for analysis and potential processing in future iterations.
     */
    private void logTrackingResponse(Shipment shipment, String waybillId, String responseBody) {
        try {
            log.debug("📊 Tracking Response Log Entry - Shipment: {}, Waybill: {}, Response length: {} chars",
                    shipment.getUuid(), waybillId, responseBody != null ? responseBody.length() : 0);
        } catch (Exception e) {
            log.error("Error logging tracking response: {}", e.getMessage());
        }
    }

    /**
     * Persist a CourierApiLog entry for every external call regardless of success/failure.
     */
    private void saveApiLog(Courier courier, String endpoint, String requestPayload,
                           String responsePayload, int httpStatus, String shipmentUuid) {
        try {
            CourierApiLog apiLog = CourierApiLog.builder()
                    .courier(courier)
                    .endpoint(endpoint)
                    .requestPayload(requestPayload)
                    .responsePayload(responsePayload)
                    .httpStatus(httpStatus == 0 ? null : httpStatus)
                    .build();
            courierApiLogRepository.save(apiLog);
            log.debug("✅ Saved CourierApiLog for shipment {} with HTTP status: {}", shipmentUuid, httpStatus);
        } catch (Exception ex) {
            log.error("Failed to save CourierApiLog for shipment {}: {}", shipmentUuid, ex.getMessage(), ex);
        }
    }
}
