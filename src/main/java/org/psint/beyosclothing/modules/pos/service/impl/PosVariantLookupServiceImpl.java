package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.service.PosVariantLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * POS Variant Lookup Service Implementation
 * Uses RabbitMQ to query Product module for variant information
 * Decouples POS module from direct Product module dependency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosVariantLookupServiceImpl implements PosVariantLookupService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.product:beyos.exchange.product}")
    private String productExchange;

    @Override
    public Long getVariantIdByUuid(String variantUuid) {
        if (variantUuid == null || variantUuid.trim().isEmpty()) {
            return null;
        }

        log.debug("Requesting variant ID for UUID: {} via RabbitMQ, exchange={}", variantUuid, productExchange);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("variantUuid", variantUuid);
            request.put("requestType", "UUID_TO_ID");

            // Do NOT call rabbitTemplate.setReplyTimeout() — it mutates the shared singleton
            Object response = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "pos.variant.lookup.request",
                    request
            );

            log.debug("Received variant lookup response: {}", response);

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");
                if (Boolean.TRUE.equals(success)) {
                    Object variantIdObj = responseMap.get("variantId");
                    if (variantIdObj instanceof Number) {
                        Long variantId = ((Number) variantIdObj).longValue();
                        log.debug("Variant UUID {} resolved to ID {}", variantUuid, variantId);
                        return variantId;
                    }
                } else {
                    log.warn("Variant lookup failed for UUID {}: {}", variantUuid, responseMap.get("errorMessage"));
                }
            }

            log.warn("Variant not found for UUID: {}", variantUuid);
            return null;

        } catch (Exception e) {
            log.error("Error looking up variant ID for UUID {} via RabbitMQ", variantUuid, e);
            return null;
        }
    }

    @Override
    public String getVariantUuidById(Long variantId) {
        if (variantId == null) {
            return null;
        }

        log.debug("Requesting variant UUID for ID: {} via RabbitMQ, exchange={}", variantId, productExchange);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("variantId", variantId);
            request.put("requestType", "ID_TO_UUID");

            // Do NOT call rabbitTemplate.setReplyTimeout() — it mutates the shared singleton
            Object response = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "pos.variant.lookup.request",
                    request
            );

            log.debug("Received variant lookup response: {}", response);

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");
                if (Boolean.TRUE.equals(success)) {
                    Object variantUuidObj = responseMap.get("variantUuid");
                    if (variantUuidObj instanceof String) {
                        String variantUuid = (String) variantUuidObj;
                        log.debug("Variant ID {} resolved to UUID {}", variantId, variantUuid);
                        return variantUuid;
                    }
                } else {
                    log.warn("Variant lookup failed for ID {}: {}", variantId, responseMap.get("errorMessage"));
                }
            }

            log.warn("Variant not found for ID: {}", variantId);
            return null;

        } catch (Exception e) {
            log.error("Error looking up variant UUID for ID {} via RabbitMQ", variantId, e);
            return null;
        }
    }
}
