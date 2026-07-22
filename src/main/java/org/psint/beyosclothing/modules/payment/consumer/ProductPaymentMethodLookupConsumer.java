package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment Method Lookup Consumer for Product Module
 * Handles Map-based RPC requests from Product module during product creation.
 * NO cross-module DTO dependencies — uses Map<String, Object> for request and response.
 *
 * Request:  { requestId, paymentMethodUuids: [uuid1, uuid2, ...] }
 * Response: { requestId, found, paymentMethods: [ { id, uuid, code, name, isActive }, ... ], errorMessage }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductPaymentMethodLookupConsumer {

    private final PaymentMethodRepository paymentMethodRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.product-payment-methods-lookup-request:product.payment.methods.lookup.request}")
    public Map<String, Object> handleProductPaymentMethodLookup(Map<String, Object> request) {
        String requestId = request != null ? (String) request.get("requestId") : null;
        log.info("📦 [PRODUCT PAYMENT METHOD LOOKUP] requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            if (request == null) {
                response.put("found", false);
                response.put("errorMessage", "Request is null");
                return response;
            }

            @SuppressWarnings("unchecked")
            List<String> paymentMethodUuids = (List<String>) request.get("paymentMethodUuids");

            if (paymentMethodUuids == null || paymentMethodUuids.isEmpty()) {
                response.put("found", false);
                response.put("errorMessage", "paymentMethodUuids is required and must not be empty");
                return response;
            }

            List<Map<String, Object>> paymentMethods = new ArrayList<>();
            List<String> notFoundUuids = new ArrayList<>();

            for (String uuid : paymentMethodUuids) {
                PaymentMethodEntity entity = paymentMethodRepository.findByUuid(uuid).orElse(null);
                if (entity == null) {
                    log.warn("[PRODUCT PAYMENT METHOD LOOKUP] Payment method not found - UUID: {}", uuid);
                    notFoundUuids.add(uuid);
                    continue;
                }

                Map<String, Object> pm = new HashMap<>();
                pm.put("id", entity.getId());
                pm.put("uuid", entity.getUuid());
                pm.put("code", entity.getCode());
                pm.put("name", entity.getName());
                pm.put("type", entity.getType() != null ? entity.getType().name() : null);
                pm.put("isActive", entity.getIsActive());
                paymentMethods.add(pm);

                log.debug("[PRODUCT PAYMENT METHOD LOOKUP] Found - UUID: {}, Code: {}, Name: {}",
                        entity.getUuid(), entity.getCode(), entity.getName());
            }

            response.put("found", !paymentMethods.isEmpty());
            response.put("paymentMethods", paymentMethods);

            if (!notFoundUuids.isEmpty()) {
                response.put("notFoundUuids", notFoundUuids);
                log.warn("[PRODUCT PAYMENT METHOD LOOKUP] Some UUIDs not found: {}", notFoundUuids);
            }

            log.info("[PRODUCT PAYMENT METHOD LOOKUP] Resolved {}/{} payment methods for requestId={}",
                    paymentMethods.size(), paymentMethodUuids.size(), requestId);

            return response;

        } catch (Exception e) {
            log.error("[PRODUCT PAYMENT METHOD LOOKUP] Error processing request - requestId={}", requestId, e);
            response.put("found", false);
            response.put("errorMessage", "Error: " + e.getMessage());
            return response;
        }
    }
}

