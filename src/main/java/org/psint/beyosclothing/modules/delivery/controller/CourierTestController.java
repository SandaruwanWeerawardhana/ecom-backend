package org.psint.beyosclothing.modules.delivery.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.service.CourierResponseProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test Controller for Courier Tracking Status Updates
 * Public API for testing delivery status changes and the complete workflow
 * NOTE: This is for testing purposes only
 */
@RestController
@RequestMapping("/api/v1/test/courier")
@RequiredArgsConstructor
@Slf4j
public class CourierTestController {

    private final CourierResponseProcessingService responseProcessingService;

    /**
     * Simulate a delivered status update from Koombiyo API
     * This allows manual testing of the entire delivery workflow without waiting for actual courier updates
     *
     * POST /api/v1/test/courier/simulate-delivered
     * Body: {
     *   "waybillId": "459654131",
     *   "orderstatus": "Delivered",
     *   "deliverystatus": "6",
     *   "district": "Colombo",
     *   "recever": "John Doe",
     *   "deliveryaddress": "123 Main St, Colombo",
     *   "codamount": "7000.00",
     *   "deliverycharge": "400.00"
     * }
     */
    @PostMapping("/simulate-delivered")
    public ResponseEntity<?> simulateDeliveredStatus(@RequestBody Map<String, Object> orderData) {
        try {
            log.info("🧪 TEST API: Simulating delivered status for waybill: {}", orderData.get("waybill_id"));

            // Validate required fields
            if (!orderData.containsKey("waybill_id") && !orderData.containsKey("waybillId")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "waybill_id is required"
                ));
            }

            // Normalize field names (handle both snake_case and camelCase)
            normalizeOrderData(orderData);

            // Create the mock API response format
            Map<String, Object> mockResponse = new HashMap<>();
            List<Map<String, Object>> custOrders = List.of(orderData);
            mockResponse.put("cust_orders", custOrders);
            mockResponse.put("rowcount", 1);

            log.info("🧪 Processing mock Koombiyo response: {}", mockResponse);

            // Process the response using the same service as real polling
            responseProcessingService.processKoombiyoTrackingResponse(mockResponse);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Delivered status simulated successfully",
                    "waybill_id", orderData.get("waybill_id"),
                    "orderstatus", orderData.get("orderstatus"),
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("❌ Error in simulate-delivered API: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Simulate any custom status update from Koombiyo API
     * Allows testing different status scenarios (processing, out for delivery, failed, etc.)
     *
     * POST /api/v1/test/courier/simulate-status
     * Body: {
     *   "waybillId": "459654131",
     *   "orderstatus": "OUT_FOR_DELIVERY",
     *   "deliverystatus": "5",
     *   "district": "Colombo",
     *   "recever": "John Doe",
     *   "deliveryaddress": "123 Main St, Colombo"
     * }
     */
    @PostMapping("/simulate-status")
    public ResponseEntity<?> simulateCustomStatus(@RequestBody Map<String, Object> orderData) {
        try {
            String orderstatus = (String) orderData.getOrDefault("orderstatus", "PROCESSING");
            log.info("🧪 TEST API: Simulating status: {} for waybill: {}", orderstatus, orderData.get("waybill_id"));

            // Validate required fields
            if (!orderData.containsKey("waybill_id") && !orderData.containsKey("waybillId")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "waybill_id is required"
                ));
            }

            // Normalize field names
            normalizeOrderData(orderData);

            // Create mock response
            Map<String, Object> mockResponse = new HashMap<>();
            List<Map<String, Object>> custOrders = List.of(orderData);
            mockResponse.put("cust_orders", custOrders);
            mockResponse.put("rowcount", 1);

            log.info("🧪 Processing mock Koombiyo response with status: {}", orderstatus);

            // Process the response
            responseProcessingService.processKoombiyoTrackingResponse(mockResponse);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Status simulated successfully",
                    "waybill_id", orderData.get("waybill_id"),
                    "orderstatus", orderstatus,
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("❌ Error in simulate-status API: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Normalize order data to handle both snake_case (from API) and camelCase (from API calls)
     */
    private void normalizeOrderData(Map<String, Object> orderData) {
        // Normalize waybill_id / waybillId
        if (!orderData.containsKey("waybill_id") && orderData.containsKey("waybillId")) {
            orderData.put("waybill_id", orderData.get("waybillId"));
        }
        if (!orderData.containsKey("waybillId") && orderData.containsKey("waybill_id")) {
            orderData.put("waybillId", orderData.get("waybill_id"));
        }

        // Ensure default values
        orderData.putIfAbsent("orderstatus", "PROCESSING");
        orderData.putIfAbsent("deliverystatus", "0");
        orderData.putIfAbsent("district", "Unknown");
    }

    /**
     * Test endpoint to verify the service is running
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Courier Test Controller",
                "timestamp", System.currentTimeMillis()
        ));
    }
}

