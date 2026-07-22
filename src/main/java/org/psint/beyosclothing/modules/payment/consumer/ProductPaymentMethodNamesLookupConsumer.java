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
 * Consumer for product payment method name lookups by ID.
 * Called by Product module to get human-readable names for payment methods
 * stored in the product_payment_method_mappings table.
 *
 * Request:  { requestId, paymentMethodIds: [1, 2, ...] }
 * Response: { requestId, found, names: ["Cash on Delivery", "Credit/Debit Cards"], paymentMethods: [...] }
 *
 * NO cross-module DTO dependencies — uses Map<String, Object>.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductPaymentMethodNamesLookupConsumer {

    private final PaymentMethodRepository paymentMethodRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.product-payment-methods-names-lookup-request:product.payment.methods.names.lookup.request}")
    public Map<String, Object> handleProductPaymentMethodNamesLookup(Map<String, Object> request) {
        String requestId = request != null ? (String) request.get("requestId") : null;
        log.info("📦 [PRODUCT PAYMENT METHOD NAMES LOOKUP] requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            if (request == null) {
                response.put("found", false);
                response.put("errorMessage", "Request is null");
                return response;
            }

            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) request.get("paymentMethodIds");

            if (rawIds == null || rawIds.isEmpty()) {
                response.put("found", false);
                response.put("errorMessage", "paymentMethodIds is required and must not be empty");
                return response;
            }

            List<Long> paymentMethodIds = new ArrayList<>();
            for (Number n : rawIds) {
                paymentMethodIds.add(n.longValue());
            }

            List<String> names = new ArrayList<>();
            List<Map<String, Object>> paymentMethods = new ArrayList<>();

            for (Long id : paymentMethodIds) {
                PaymentMethodEntity entity = paymentMethodRepository.findById(id).orElse(null);
                if (entity == null) {
                    log.warn("[PRODUCT PAYMENT METHOD NAMES LOOKUP] Payment method not found - ID: {}", id);
                    continue;
                }

                names.add(entity.getName());

                Map<String, Object> pm = new HashMap<>();
                pm.put("id", entity.getId());
                pm.put("uuid", entity.getUuid());
                pm.put("code", entity.getCode());
                pm.put("name", entity.getName());
                pm.put("type", entity.getType() != null ? entity.getType().name() : null);
                pm.put("isActive", entity.getIsActive());
                paymentMethods.add(pm);

                log.debug("[PRODUCT PAYMENT METHOD NAMES LOOKUP] Found - ID: {}, Code: {}, Name: {}",
                        entity.getId(), entity.getCode(), entity.getName());
            }

            response.put("found", !names.isEmpty());
            response.put("names", names);
            response.put("paymentMethods", paymentMethods);

            log.info("[PRODUCT PAYMENT METHOD NAMES LOOKUP] Resolved {}/{} names for requestId={}",
                    names.size(), paymentMethodIds.size(), requestId);

            return response;

        } catch (Exception e) {
            log.error("[PRODUCT PAYMENT METHOD NAMES LOOKUP] Error - requestId={}", requestId, e);
            response.put("found", false);
            response.put("errorMessage", "Error: " + e.getMessage());
            return response;
        }
    }
}

