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
 * Reseller Name Lookup Consumer
 * Handles RabbitMQ RPC requests for reseller name lookup by resellerId.
 * Uses Map-based messaging to avoid cross-module DTO dependencies.
 *
 * Request keys:  resellerId
 * Response keys: found, fullName, phone, resellerId (verification)
 *
 * Used by: Admin Order Service to enrich order list responses with reseller names/phone
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerNameLookupConsumer {

    private final ResellerRepository resellerRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.reseller-name-lookup-request:reseller.name.lookup.request}")
    @Transactional(value = "resellerTransactionManager", readOnly = true)
    public Map<String, Object> handleResellerNameLookup(Map<String, Object> request) {

        log.debug("🔍 [RESELLER NAME LOOKUP] Received request");

        Map<String, Object> response = new HashMap<>();

        try {
            Long resellerId = null;
            Object resellerIdObj = request != null ? request.get("resellerId") : null;
            if (resellerIdObj instanceof Number) {
                resellerId = ((Number) resellerIdObj).longValue();
            }

            if (resellerId == null) {
                log.warn("[RESELLER NAME LOOKUP] Missing resellerId in request");
                response.put("found", false);
                response.put("error", "resellerId is required");
                return response;
            }

            Reseller reseller = resellerRepository.findById(resellerId).orElse(null);

            if (reseller == null) {
                log.warn("[RESELLER NAME LOOKUP] Reseller not found for resellerId={}", resellerId);
                response.put("found", false);
                return response;
            }

            response.put("found", true);
            response.put("resellerId", reseller.getId());
            response.put("fullName", reseller.getFullName());
            response.put("phone", reseller.getPhone());

            log.info("✅ [RESELLER NAME LOOKUP] Found reseller: resellerId={}, fullName={}",
                resellerId, reseller.getFullName());

        } catch (Exception e) {
            log.error("❌ [RESELLER NAME LOOKUP] Error processing request", e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
        }

        return response;
    }
}

