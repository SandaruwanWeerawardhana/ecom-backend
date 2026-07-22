package org.psint.beyosclothing.modules.cart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.*;
import org.psint.beyosclothing.modules.cart.service.CrossModuleLookupService;
import org.psint.beyosclothing.shared.dto.CustomerLookupRequest;
import org.psint.beyosclothing.shared.dto.CustomerLookupResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Cross-Module Lookup Service Implementation
 * Uses RabbitMQ Request-Reply pattern for synchronous cross-module communication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrossModuleLookupServiceImpl implements CrossModuleLookupService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchange;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.exchange.promotion:beyos.exchange.promotion}")
    private String promotionExchange;

    private static final long LOOKUP_TIMEOUT_MS = 5000; // 5 seconds timeout

    @Override
    public CustomerLookupResponse lookupCustomerByUuid(String customerUuid) {
        log.debug("Looking up customer by UUID: {}", customerUuid);

        try {
            String requestId = UUID.randomUUID().toString();
            CustomerLookupRequest request = CustomerLookupRequest.builder()
                    .customerUuid(customerUuid)
                    .requestId(requestId)
                    .build();

            Object rawResponse = rabbitTemplate
                    .convertSendAndReceive(
                            customerExchange,
                            "customer.lookup.request",
                            request
                    );
            CustomerLookupResponse response = convertResponse(rawResponse, CustomerLookupResponse.class);

            if (response == null) {
                log.error("RabbitMQ RPC returned null - no response from Customer module for UUID: {}. "
                        + "Check: 1) Customer module is running, 2) customer.lookup.request queue has a listener, "
                        + "3) exchange/binding is correct", customerUuid);
                return null; // RPC failure
            }

            if (response.getFound()) {
                log.debug("Customer found - ID: {}, UUID: {}", response.getCustomerId(), response.getCustomerUuid());
            } else {
                log.warn("Customer not found for UUID: {}", customerUuid);
            }
            return response; // Always return when we got a response (found or not found)

        } catch (Exception e) {
            log.error("Error looking up customer by UUID: {}", customerUuid, e);
            return null; // RPC exception
        }
    }

    @Override
    public ProductLookupResponse lookupProductByUuid(String productUuid) {
        log.debug("Looking up product by UUID: {}", productUuid);

        try {
            String requestId = UUID.randomUUID().toString();
            ProductLookupRequest request = ProductLookupRequest.builder()
                    .productUuid(productUuid)
                    .requestId(requestId)
                    .build();

            Object rawResponse = rabbitTemplate
                    .convertSendAndReceive(
                            productExchange,
                            "product.lookup.request",
                            request
                    );
            ProductLookupResponse response = convertResponse(rawResponse, ProductLookupResponse.class);

            if (response == null) {
                log.error("RabbitMQ RPC returned null - no response from Product module for UUID: {}", productUuid);
                return null; // RPC failure
            }

            if (response.getFound()) {
                log.debug("Product found - ID: {}, UUID: {}", response.getProductId(), response.getProductUuid());
            } else {
                log.warn("Product not found for UUID: {}", productUuid);
            }
            return response;

        } catch (Exception e) {
            log.error("Error looking up product by UUID: {}", productUuid, e);
            return null;
        }
    }

    @Override
    public ProductLookupResponse lookupProductVariantByUuid(String productUuid, String variantUuid) {
        log.debug("Looking up product variant - Product UUID: {}, Variant UUID: {}", productUuid, variantUuid);

        try {
            String requestId = UUID.randomUUID().toString();
            ProductLookupRequest request = ProductLookupRequest.builder()
                    .productUuid(productUuid)
                    .variantUuid(variantUuid)
                    .requestId(requestId)
                    .build();

            Object rawResponse = rabbitTemplate
                    .convertSendAndReceive(
                            productExchange,
                            "product.lookup.request",
                            request
                    );
            ProductLookupResponse response = convertResponse(rawResponse, ProductLookupResponse.class);

            if (response == null) {
                log.error("RabbitMQ RPC returned null - no response from Product module for Product UUID: {}, Variant UUID: {}",
                        productUuid, variantUuid);
                return null; // RPC failure
            }

            if (response.getFound()) {
                log.debug("Product variant found - Product ID: {}, Variant ID: {}",
                        response.getProductId(), response.getVariantId());
            } else {
                log.warn("Product variant not found - Product UUID: {}, Variant UUID: {}",
                        productUuid, variantUuid);
            }
            return response;

        } catch (Exception e) {
            log.error("Error looking up product variant - Product UUID: {}, Variant UUID: {}",
                    productUuid, variantUuid, e);
            return null;
        }
    }

    @Override
    public StockCheckResponse checkStockAvailability(Long productId, Long variantId, Integer quantity) {
        log.debug("Checking stock - Product ID: {}, Variant ID: {}, Quantity: {}",
                productId, variantId, quantity);

        try {
            String requestId = UUID.randomUUID().toString();
            StockCheckRequest request = StockCheckRequest.builder()
                    .requestId(requestId)
                    .productId(productId)
                    .variantId(variantId)
                    .requestedQuantity(quantity)
                    .build();

            Object rawResponse = rabbitTemplate
                    .convertSendAndReceive(
                            inventoryExchange,
                            "inventory.stock.check",
                            request
                    );
            StockCheckResponse response = convertResponse(rawResponse, StockCheckResponse.class);

            if (response != null && response.getFound()) {
                log.debug("Stock check result - Available: {}, Quantity: {}",
                        response.getIsAvailable(), response.getAvailableStock());
                return response;
            } else {
                log.warn("Stock information not found for Product ID: {}, Variant ID: {}",
                        productId, variantId);
                // Return default response
                return StockCheckResponse.builder()
                        .requestId(requestId)
                        .productId(productId)
                        .variantId(variantId)
                        .availableStock(0)
                        .isAvailable(false)
                        .allowBackorder(false)
                        .found(false)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error checking stock - Product ID: {}, Variant ID: {}",
                    productId, variantId, e);
            // Return default response on error
            return StockCheckResponse.builder()
                    .productId(productId)
                    .variantId(variantId)
                    .availableStock(0)
                    .isAvailable(false)
                    .allowBackorder(false)
                    .found(false)
                    .build();
        }
    }

    @Override
    public PromoValidationResponse validatePromoCode(PromoValidationRequest request) {
        log.debug("Validating promo code: {}", request.getPromoCode());

        try {
            Object rawResponse = rabbitTemplate
                    .convertSendAndReceive(
                            promotionExchange,
                            "promotion.validate.request",
                            request
                    );
            PromoValidationResponse response = convertResponse(rawResponse, PromoValidationResponse.class);

            if (response != null && response.getFound()) {
                log.debug("Promo code validation result - Valid: {}, Discount: {}",
                        response.getIsValid(), response.getCalculatedDiscount());
                return response;
            } else {
                log.warn("Promo code not found or invalid: {}", request.getPromoCode());
                return PromoValidationResponse.builder()
                        .requestId(request.getRequestId())
                        .promoCode(request.getPromoCode())
                        .isValid(false)
                        .found(false)
                        .errorMessage("Promo code not found")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error validating promo code: {}", request.getPromoCode(), e);
            return PromoValidationResponse.builder()
                    .requestId(request.getRequestId())
                    .promoCode(request.getPromoCode())
                    .isValid(false)
                    .found(false)
                    .errorMessage("Error validating promo code: " + e.getMessage())
                    .build();
        }
    }

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

            Object rawResponse = rabbitTemplate
                    .convertSendAndReceive(
                            productExchange,
                            "product.details.lookup.request",
                            request
                    );
            ProductDetailsLookupResponse response = convertResponse(rawResponse, ProductDetailsLookupResponse.class);

            if (response != null && response.getFound()) {
                log.debug("Product details found - Product ID: {}, Title: {}", 
                        response.getProductId(), response.getProductTitle());
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

    private <T> T convertResponse(Object rawResponse, Class<T> targetType) {
        if (rawResponse == null) {
            return null;
        }

        if (targetType.isInstance(rawResponse)) {
            return targetType.cast(rawResponse);
        }

        if (rawResponse instanceof byte[] bytes) {
            try {
                return objectMapper.readValue(bytes, targetType);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize RabbitMQ response bytes to " + targetType.getSimpleName(), e);
            }
        }

        if (rawResponse instanceof String json) {
            try {
                return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), targetType);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize RabbitMQ JSON response to " + targetType.getSimpleName(), e);
            }
        }

        return objectMapper.convertValue(rawResponse, targetType);
    }
}
