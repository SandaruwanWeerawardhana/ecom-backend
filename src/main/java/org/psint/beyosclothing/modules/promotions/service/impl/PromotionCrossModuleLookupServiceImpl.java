package org.psint.beyosclothing.modules.promotions.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupRequest;
import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupResponse;
import org.psint.beyosclothing.modules.promotions.service.CrossModuleLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cross-Module Lookup Service Implementation for Promotions Module
 * Uses RabbitMQ Request-Reply pattern for synchronous cross-module communication
 */
@Service("promotionCrossModuleLookupService") // ✅ Explicit bean name to avoid conflict
@RequiredArgsConstructor
@Slf4j
public class PromotionCrossModuleLookupServiceImpl implements CrossModuleLookupService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    @Override
    public ProductDetailsLookupResponse lookupProductDetailsById(Long productId, Long variantId) {
        log.debug("Looking up product details by ID - Product ID: {}, Variant ID: {}", productId, variantId);

        try {
            String requestId = UUID.randomUUID().toString();
            ProductDetailsLookupRequest request = ProductDetailsLookupRequest.builder()
                    .requestId(requestId)
                    .productId(productId)
                    .variantId(variantId)
                    .build();

            ProductDetailsLookupResponse response = (ProductDetailsLookupResponse) rabbitTemplate
                    .convertSendAndReceive(
                            productExchange,
                            "product.details.lookup.request",
                            request
                    );

            if (response != null && response.getFound()) {
                log.debug("Product details found - Product ID: {}, Title: {}, Price: {}",
                        response.getProductId(), response.getProductTitle(), response.getShowcasePrice());
                return response;
            } else {
                log.warn("Product details not found for Product ID: {}", productId);
                return null;
            }

        } catch (Exception e) {
            log.error("Error looking up product details by ID: {}", productId, e);
            return null;
        }
    }
}
