package org.psint.beyosclothing.modules.products.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.psint.beyosclothing.modules.products.repository.ProductVariantRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer for POS Variant Lookup Requests
 * Handles variant UUID ↔ ID conversion requests from POS module
 * Decouples POS module from direct Product module dependency
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PosVariantLookupConsumer {

    private final ProductVariantRepository variantRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-variant-lookup-request:pos.variant.lookup.request}")
    public Map<String, Object> handleVariantLookupRequest(Map<String, Object> request) {
        String requestId = (String) request.get("requestId");
        String requestType = (String) request.get("requestType");

        log.debug("🔍 [VARIANT LOOKUP] Received request: type={}, id={}", requestType, requestId);

        try {
            if ("UUID_TO_ID".equals(requestType)) {
                return handleUuidToIdRequest(requestId, request);
            } else if ("ID_TO_UUID".equals(requestType)) {
                return handleIdToUuidRequest(requestId, request);
            } else {
                log.warn("Unknown request type: {}", requestType);
                return buildErrorResponse(requestId, "Unknown request type: " + requestType);
            }
        } catch (Exception e) {
            log.error("❌ [VARIANT LOOKUP] Error processing request", e);
            return buildErrorResponse(requestId, "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Handle UUID to ID conversion request
     */
    private Map<String, Object> handleUuidToIdRequest(String requestId, Map<String, Object> request) {
        String variantUuid = (String) request.get("variantUuid");

        if (variantUuid == null || variantUuid.trim().isEmpty()) {
            log.warn("Missing variantUuid in request");
            return buildErrorResponse(requestId, "Missing variantUuid");
        }

        log.debug("Looking up variant by UUID: {}", variantUuid);

        ProductVariant variant = variantRepository.findByUuid(variantUuid).orElse(null);

        if (variant == null) {
            log.warn("Variant not found for UUID: {}", variantUuid);
            return buildErrorResponse(requestId, "Variant not found");
        }

        log.info("✅ [VARIANT LOOKUP] UUID {} → ID {}", variantUuid, variant.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", true);
        response.put("variantId", variant.getId());
        response.put("variantUuid", variant.getUuid());
        response.put("sku", variant.getSku());
        response.put("isActive", variant.getIsActive());
        return response;
    }

    /**
     * Handle ID to UUID conversion request
     */
    private Map<String, Object> handleIdToUuidRequest(String requestId, Map<String, Object> request) {
        Object variantIdObj = request.get("variantId");

        if (variantIdObj == null) {
            log.warn("Missing variantId in request");
            return buildErrorResponse(requestId, "Missing variantId");
        }

        Long variantId = ((Number) variantIdObj).longValue();

        log.debug("Looking up variant by ID: {}", variantId);

        ProductVariant variant = variantRepository.findById(variantId).orElse(null);

        if (variant == null) {
            log.warn("Variant not found for ID: {}", variantId);
            return buildErrorResponse(requestId, "Variant not found");
        }

        log.info("✅ [VARIANT LOOKUP] ID {} → UUID {}", variantId, variant.getUuid());

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", true);
        response.put("variantId", variant.getId());
        response.put("variantUuid", variant.getUuid());
        response.put("sku", variant.getSku());
        response.put("isActive", variant.getIsActive());
        return response;
    }

    /**
     * Build error response
     */
    private Map<String, Object> buildErrorResponse(String requestId, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", false);
        response.put("errorMessage", errorMessage);
        return response;
    }
}

