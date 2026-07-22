package org.psint.beyosclothing.modules.products.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.entity.ProductPaymentMethodMapping;
import org.psint.beyosclothing.modules.products.repository.ProductPaymentMethodMappingRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RabbitMQ Consumer: Product payment method mappings lookup.
 *
 * Called by Payment module with a list of product IDs to get the
 * payment method IDs/codes allowed for each product.
 *
 * Request:  { requestId, productIds: [1, 2, 3] }
 * Response: { requestId, found,
 *             mappings: [ { productId, paymentMethodId, paymentMethodCode }, ... ] }
 *
 * NO cross-module DTO dependencies — uses Map<String, Object>.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductPaymentMethodMappingConsumer {

    private final ProductPaymentMethodMappingRepository mappingRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.product-payment-method-mapping-lookup-request:product.payment.method.mapping.lookup.request.queue}")
    public Map<String, Object> handleProductPaymentMethodMappingLookup(Map<String, Object> request) {
        String requestId = request != null ? (String) request.get("requestId") : null;
        log.info("📦 [PRODUCT PAYMENT METHOD MAPPING LOOKUP] requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            if (request == null) {
                response.put("found", false);
                response.put("errorMessage", "Request is null");
                return response;
            }

            @SuppressWarnings("unchecked")
            List<Number> rawProductIds = (List<Number>) request.get("productIds");

            if (rawProductIds == null || rawProductIds.isEmpty()) {
                response.put("found", false);
                response.put("errorMessage", "productIds is required and must not be empty");
                return response;
            }

            List<Long> productIds = rawProductIds.stream()
                    .map(Number::longValue)
                    .collect(Collectors.toList());

            // Fetch active mappings for all requested product IDs in one query
            List<ProductPaymentMethodMapping> mappings =
                    mappingRepository.findActiveByProductIdIn(productIds);

            List<Map<String, Object>> mappingList = mappings.stream()
                    .map(m -> {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("productId", m.getProductId());
                        entry.put("paymentMethodId", m.getPaymentMethodId());
                        entry.put("paymentMethodCode", m.getPaymentMethodCode());
                        return entry;
                    })
                    .collect(Collectors.toList());

            response.put("found", !mappingList.isEmpty());
            response.put("mappings", mappingList);

            log.info("[PRODUCT PAYMENT METHOD MAPPING LOOKUP] Found {} mappings for {} products, requestId={}",
                    mappingList.size(), productIds.size(), requestId);

            return response;

        } catch (Exception e) {
            log.error("[PRODUCT PAYMENT METHOD MAPPING LOOKUP] Error - requestId={}", requestId, e);
            response.put("found", false);
            response.put("errorMessage", "Error: " + e.getMessage());
            return response;
        }
    }
}

