package org.psint.beyosclothing.modules.resellers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Reseller Details Lookup Consumer
 * Handles RabbitMQ RPC requests for reseller details lookup with userId matching.
 * Uses Map-based messaging to avoid cross-module DTO dependencies.
 *
 * Request keys:  requestId, userId
 * Response keys: requestId, found, resellerId, uuid, userId (verification),
 *                firstName, lastName, fullName, email, phone, phoneNumber, status, isActive
 *
 * ✅ VERIFICATION: Returns both userId and resellerId so auth module can confirm match
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerDetailsLookupConsumer {

    private final ResellerRepository resellerRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.reseller-details-lookup-request:reseller.details.lookup.request}")
    @Transactional(value = "resellerTransactionManager", readOnly = true)
    public Map<String, Object> handleResellerDetailsLookup(Map<String, Object> request) {

        String requestId = request != null ? (String) request.get("requestId") : null;
        log.debug("🔍 [RESELLER LOOKUP] Received request: requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            Long userId = null;
            Object userIdObj = request != null ? request.get("userId") : null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            }

            if (userId == null) {
                log.warn("[RESELLER LOOKUP] Missing userId in request: {}", requestId);
                response.put("found", false);
                response.put("error", "userId is required");
                return response;
            }

            Reseller reseller = resellerRepository.findByUserId(userId).orElse(null);

            if (reseller == null) {
                log.warn("[RESELLER LOOKUP] Reseller not found for userId={}", userId);
                response.put("found", false);
                return response;
            }

            response.put("found", true);
            response.put("resellerId", reseller.getId());
            response.put("uuid", reseller.getUuid());
            response.put("userId", reseller.getUserId());
            response.put("firstName", reseller.getFirstName());
            response.put("lastName", reseller.getLastName());
            response.put("fullName", reseller.getFullName());
            response.put("email", reseller.getEmail());
            response.put("phone", reseller.getPhone());
            response.put("phoneNumber", reseller.getPhone());
            response.put("status", reseller.getStatus() != null ? reseller.getStatus().name() : null);
            response.put("isActive", reseller.getIsActive());

            log.info("✅ [RESELLER LOOKUP] Found reseller matched: userId={} → resellerId={}, uuid={}, email={}",
                userId, reseller.getId(), reseller.getUuid(), reseller.getEmail());

        } catch (Exception e) {
            log.error("❌ [RESELLER LOOKUP] Error processing request requestId={}", requestId, e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
        }

        return response;
    }
}

