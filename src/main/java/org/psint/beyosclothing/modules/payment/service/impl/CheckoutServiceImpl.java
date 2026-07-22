package org.psint.beyosclothing.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.core.exception.ServiceException;
import org.psint.beyosclothing.modules.payment.dto.external.*;
import org.psint.beyosclothing.modules.payment.dto.request.CheckoutRequest;
import org.psint.beyosclothing.modules.payment.dto.response.CheckoutResponse;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.psint.beyosclothing.modules.payment.service.CheckoutService;
import org.psint.beyosclothing.modules.payment.service.PaymentCrossModuleLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Checkout Service Implementation
 * Orchestrates checkout preparation by aggregating data from multiple modules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutServiceImpl implements CheckoutService {

    private final PaymentCrossModuleLookupService crossModuleLookupService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.cart:beyos.exchange.cart}")
    private String cartExchange;

    @Value("${app.rabbitmq.exchange.product:beyos.exchange.product}")
    private String productExchange;

    private static final String CHECKOUT_CACHE_PREFIX = "checkout:";
    private static final Duration CHECKOUT_CACHE_TTL = Duration.ofMinutes(5);

    @Override
    @Transactional(readOnly = true)
    public CheckoutResponse prepareCheckout(String customerUuid, String guestSessionToken, CheckoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Checkout request is required");
        }

        String normalizedCustomerUuid = hasText(customerUuid) ? customerUuid.trim() : null;
        String normalizedGuestSessionToken = hasText(guestSessionToken) ? guestSessionToken.trim() : null;
        String lookupCustomerUuid = normalizedCustomerUuid;
        String lookupGuestSessionToken = normalizedGuestSessionToken;

        log.info("Preparing checkout - Customer UUID: {}, Guest Token: {}, Address ID: {}, Courier: {}, Payment Method: {}",
                normalizedCustomerUuid, normalizedGuestSessionToken != null ? "***" : null, request.getAddressId(), request.getCourierUuid(), request.getPaymentMethodUuid());

        // Validate inputs
        if (lookupCustomerUuid == null && lookupGuestSessionToken == null) {
            throw new IllegalArgumentException("Either customer UUID or guest session token must be provided");
        }

        log.info("Checkout cart lookup identity - Mode: {}, Customer UUID: {}, Guest Token Used: {}",
                lookupCustomerUuid != null && lookupGuestSessionToken != null ? "CUSTOMER_AND_GUEST"
                        : lookupCustomerUuid != null ? "CUSTOMER" : "GUEST",
                lookupCustomerUuid,
                lookupGuestSessionToken != null);

        // Step 1: Get cart items with product details
        CartItemsLookupResponse cartResponse = crossModuleLookupService.getCartItemsForCheckout(
                lookupCustomerUuid,
                lookupGuestSessionToken,
                request.getSelectedItemUuids()
        );
        log.info("Cart items retrieved - Found: {}", cartResponse != null ? cartResponse.getFound() : false);

        if (cartResponse == null || !Boolean.TRUE.equals(cartResponse.getFound())
                || cartResponse.getItems() == null || cartResponse.getItems().isEmpty()) {
            log.warn("Cart lookup failed - cartResponse isNull: {}, found: {}, itemsSize: {}",
                    cartResponse == null,
                    cartResponse != null ? cartResponse.getFound() : "N/A",
                    cartResponse != null && cartResponse.getItems() != null ? cartResponse.getItems().size() : "N/A");
            throw new ResourceNotFoundException("Cart", "customerUuid/guestToken",
                    lookupCustomerUuid != null ? lookupCustomerUuid : "guest");
        }

        // Step 2: Get customer delivery address (only if addressId is provided)
        CustomerAddressLookupResponse addressResponse = null;
        String customerType = "CUSTOMER";

        if (request.getAddressId() != null) {
            addressResponse = crossModuleLookupService.lookupCustomerAddress(request.getAddressId());
            if (addressResponse != null && Boolean.TRUE.equals(addressResponse.getFound())) {
                customerType = addressResponse.getCustomerType() != null ? addressResponse.getCustomerType() : "CUSTOMER";
                log.info("Customer address found - Address ID: {}, Customer Type: {}", request.getAddressId(), customerType);
            } else {
                log.warn("Customer address not found for Address ID: {}", request.getAddressId());
                // Don't throw error - might be guest or incomplete profile
            }
        } else {
            log.info("No address ID provided - skipping address lookup (guest checkout or address not selected)");
        }

        // Step 3: Get payment method details
        PaymentMethodEntity paymentMethod = paymentMethodRepository.findByUuid(request.getPaymentMethodUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method", "id", request.getPaymentMethodUuid()));

        if (!paymentMethod.getIsActive()) {
            throw new IllegalArgumentException("Selected payment method is not active");
        }

        // Step 4: Calculate shipping cost
        BigDecimal totalWeight = cartResponse.getTotalWeight() != null ? cartResponse.getTotalWeight() : BigDecimal.ZERO;

        ShippingCalculationRequest shippingRequest = ShippingCalculationRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .courierUuid(request.getCourierUuid())
                .totalWeight(totalWeight)
                .customerType(customerType)
                .paymentMethodId(paymentMethod.getId())
                .build();

        ShippingCalculationResponse shippingResponse = crossModuleLookupService.calculateShippingCost(shippingRequest);


        if (shippingResponse == null) {
            log.error("Shipping calculation failed - no response from delivery module (RPC timeout or error). Courier: {}",
                    request.getCourierUuid());
            throw new ServiceException("Unable to calculate shipping cost at the moment. Please try again shortly.");
        }

        if (!Boolean.TRUE.equals(shippingResponse.getFound())) {
            String reason = hasText(shippingResponse.getErrorMessage())
                    ? shippingResponse.getErrorMessage()
                    : "No applicable shipping rate found for the selected courier";
            log.warn("Shipping calculation unsuccessful - Courier: {}, Reason: {}", request.getCourierUuid(), reason);
            throw new ResourceNotFoundException(reason);
        }

        // Step 5: Build checkout response
        CheckoutResponse response = buildCheckoutResponse(
                cartResponse,
                addressResponse,
                shippingResponse,
                paymentMethod,
                request.getDeliveryNotes()
        );

        // Cache checkout summary for 5 minutes
        cacheCheckoutSummary(normalizedCustomerUuid, normalizedGuestSessionToken, response);

        log.info("Checkout prepared successfully - Total: {}, Items: {}",
                response.getPriceBreakdown().getTotal(), response.getItems().size());

        return response;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Build complete checkout response from aggregated data
     */
//    private CheckoutResponse buildCheckoutResponse(
//            CartItemsLookupResponse cartResponse,
//            CustomerAddressLookupResponse addressResponse,
//            ShippingCalculationResponse shippingResponse,
//            PaymentMethodEntity paymentMethod,
//            String deliveryNotes) {
//
//        // Map cart items to checkout items
//        List<CheckoutResponse.CheckoutItemDTO> items = cartResponse.getItems().stream()
//                .map(item -> CheckoutResponse.CheckoutItemDTO.builder()
//                        .itemUuid(item.getItemUuid())
//                        .productUuid(item.getProductUuid())
//                        .productTitle(item.getProductTitle())
//                        .variantUuid(item.getVariantUuid())
//                        .variantTitle(item.getVariantTitle())
//                        .quantity(item.getQuantity())
//                        .unitPrice(item.getUnitPrice())
//                        .totalPrice(item.getTotalPrice())
//                        .itemWeight(item.getItemWeight())
//                        .imageUrl(item.getImageUrl())
//                        .build())
//                .collect(Collectors.toList());
//
//        // Map delivery address
//        CheckoutResponse.DeliveryAddressDTO deliveryAddress = null;
//        if (addressResponse != null && addressResponse.getFound()) {
//            deliveryAddress = CheckoutResponse.DeliveryAddressDTO.builder()
//                    .fullName(addressResponse.getFullName())
//                    .addressLine1(addressResponse.getAddressLine1())
//                    .addressLine2(addressResponse.getAddressLine2())
//                    .city(addressResponse.getCity())
//                    .province(addressResponse.getProvince())
//                    .postalCode(addressResponse.getPostalCode())
//                    .phoneNumber(addressResponse.getPhoneNumber())
//                    .email(addressResponse.getEmail())
//                    .build();
//        }
//
//        // Map courier information
//        CheckoutResponse.CourierDTO courier = CheckoutResponse.CourierDTO.builder()
//                .courierId(shippingResponse.getCourierId())
//                .courierName(shippingResponse.getCourierName())
//                .shippingCost(shippingResponse.getShippingCost())
//                .isFreeShipping(shippingResponse.getIsFree())
//                .shippingBreakdown(shippingResponse.getBreakdown())
//                .build();
//
//        // Map payment method
//        CheckoutResponse.PaymentMethodDTO paymentMethodDTO = CheckoutResponse.PaymentMethodDTO.builder()
//                .paymentMethodId(paymentMethod.getId())
//                .paymentMethodName(paymentMethod.getName())
//                .paymentMethodCode(paymentMethod.getCode())
//                .paymentMethodType(paymentMethod.getType().name())
//                .build();
//
//        // Calculate price breakdown
//        BigDecimal subtotal = cartResponse.getSubtotal() != null ? cartResponse.getSubtotal() : BigDecimal.ZERO;
//        BigDecimal promoDiscount = cartResponse.getPromoDiscount() != null ? cartResponse.getPromoDiscount() : BigDecimal.ZERO;
//        BigDecimal shippingCost = shippingResponse.getShippingCost() != null ? shippingResponse.getShippingCost() : BigDecimal.ZERO;
//        BigDecimal total = subtotal.subtract(promoDiscount).add(shippingCost);
//
//        CheckoutResponse.PriceBreakdownDTO priceBreakdown = CheckoutResponse.PriceBreakdownDTO.builder()
//                .subtotal(subtotal)
//                .promoDiscount(promoDiscount)
//                .shippingCost(shippingCost)
//                .totalWeight(cartResponse.getTotalWeight())
//                .total(total)
//                .build();
//
//        // Build final response
//        return CheckoutResponse.builder()
//                .items(items)
//                .deliveryAddress(deliveryAddress)
//                .courier(courier)
//                .paymentMethod(paymentMethodDTO)
//                .priceBreakdown(priceBreakdown)
//                .promoCode(cartResponse.getPromoCode())
//                .deliveryNotes(deliveryNotes)
//                .build();
//    }

    /**
     * Cache checkout summary in Redis for quick retrieval
     */
    private void cacheCheckoutSummary(String customerUuid, String guestSessionToken, CheckoutResponse response) {
        try {
            if (customerUuid != null) {
                String customerCacheKey = CHECKOUT_CACHE_PREFIX + customerUuid;
                redisTemplate.opsForValue().set(customerCacheKey, response, CHECKOUT_CACHE_TTL);
                log.debug("Checkout summary cached with customer key: {}", customerCacheKey);
            }

            if (guestSessionToken != null) {
                String guestCacheKey = CHECKOUT_CACHE_PREFIX + guestSessionToken;
                redisTemplate.opsForValue().set(guestCacheKey, response, CHECKOUT_CACHE_TTL);
                log.debug("Checkout summary cached with guest key: {}", guestCacheKey);
            }
        } catch (Exception e) {
            log.warn("Failed to cache checkout summary", e);
            // Don't fail the request if caching fails
        }
    }

    /**
     * Retrieve the payment methods that are common to ALL products in the cart.
     *
     * Flow (all cross-module calls use Map — no shared DTOs):
     *   1. Ask Cart module  → get product IDs for this cartUuid
     *   2. Ask Product module → get payment method mappings for those product IDs
     *   3. Compute INTERSECTION of allowed payment method IDs across all products
     *   4. Load each candidate from this module's own repository + validate active/type
     */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getPaymentMethodsForCart(String cartUuid, String type) {
        log.info("Getting payment methods for cart UUID: {}, type filter: {}", cartUuid, type);

        // ── STEP 1: Get product IDs from cart via RabbitMQ ────────────────
        List<Long> productIds = fetchProductIdsFromCart(cartUuid);
        if (productIds == null || productIds.isEmpty()) {
            log.warn("No products found in cart UUID: {}", cartUuid);
            return Collections.emptyList();
        }
        log.info("Cart {} contains {} distinct product(s): {}", cartUuid, productIds.size(), productIds);

        // ── STEP 2: Get payment method mappings for each product ──────────
        // Response: list of { productId, paymentMethodId, paymentMethodCode }
        List<Map<String, Object>> allMappings = fetchProductPaymentMethodMappings(productIds);
        if (allMappings == null || allMappings.isEmpty()) {
            log.warn("No payment method mappings found for products: {}", productIds);
            return Collections.emptyList();
        }

        // ── STEP 3: Compute INTERSECTION ──────────────────────────────────
        // Group mappings by productId  →  Set<paymentMethodId> per product
        Map<Long, Set<Long>> methodsPerProduct = new LinkedHashMap<>();
        for (Map<String, Object> mapping : allMappings) {
            Long productId       = ((Number) mapping.get("productId")).longValue();
            Long paymentMethodId = ((Number) mapping.get("paymentMethodId")).longValue();
            methodsPerProduct
                    .computeIfAbsent(productId, k -> new LinkedHashSet<>())
                    .add(paymentMethodId);
        }

        // Products that have NO mappings at all can't be purchased → return empty
        boolean anyProductHasNoMapping = productIds.stream()
                .anyMatch(pid -> !methodsPerProduct.containsKey(pid));
        if (anyProductHasNoMapping) {
            log.warn("One or more products have no payment method mappings – returning empty list");
            return Collections.emptyList();
        }

        // Start with the payment method IDs of the first product, then intersect
        Iterator<Set<Long>> iter = methodsPerProduct.values().iterator();
        Set<Long> commonIds = new LinkedHashSet<>(iter.next());
        while (iter.hasNext()) {
            commonIds.retainAll(iter.next());
        }

        if (commonIds.isEmpty()) {
            log.info("No common payment methods found across all products in cart {}", cartUuid);
            return Collections.emptyList();
        }

        log.info("Intersection of allowed payment method IDs: {}", commonIds);

        // ── STEP 4: Load + validate from this module's own repository ─────
        List<PaymentMethodResponse> result = new ArrayList<>();
        for (Long methodId : commonIds) {
            PaymentMethodEntity entity = paymentMethodRepository.findById(methodId).orElse(null);
            if (entity == null) {
                log.warn("Payment method ID {} not found in payment module – skipping", methodId);
                continue;
            }
            if (!Boolean.TRUE.equals(entity.getIsActive())) {
                log.debug("Payment method ID {} is inactive – skipping", methodId);
                continue;
            }
            // Apply optional type filter
            if (type != null && !type.isBlank()) {
                try {
                    PaymentMethodEntity.PaymentType filterType =
                            PaymentMethodEntity.PaymentType.valueOf(type.toUpperCase());
                    if (entity.getType() != filterType) {
                        continue;
                    }
                } catch (IllegalArgumentException ex) {
                    log.warn("Invalid payment type filter '{}' – ignoring filter", type);
                }
            }
            result.add(toPaymentMethodResponse(entity));
        }

        log.info("Returning {} common payment method(s) for cart {}", result.size(), cartUuid);
        return result;
    }

    // =========================================================
    // PRIVATE HELPERS — RabbitMQ Map-based calls
    // =========================================================

    /**
     * Ask Cart module: given cartUuid → list of distinct product IDs.
     * Routing key: cart.payment.method.lookup.request
     */
    @SuppressWarnings("unchecked")
    private List<Long> fetchProductIdsFromCart(String cartUuid) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("requestId", UUID.randomUUID().toString());
            req.put("cartUuid", cartUuid);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));
            Object raw = rabbitTemplate.convertSendAndReceive(
                    cartExchange,
                    "cart.payment.method.lookup.request",
                    req
            );

            if (raw instanceof Map) {
                Map<String, Object> resp = (Map<String, Object>) raw;
                if (Boolean.TRUE.equals(resp.get("found"))) {
                    List<Number> rawIds = (List<Number>) resp.get("productIds");
                    if (rawIds != null) {
                        return rawIds.stream()
                                .map(Number::longValue)
                                .collect(Collectors.toList());
                    }
                } else {
                    log.warn("Cart lookup for payment methods failed: {}", resp.get("errorMessage"));
                }
            }
        } catch (Exception e) {
            log.error("Error fetching product IDs from cart module for cart {}", cartUuid, e);
        }
        return Collections.emptyList();
    }

    /**
     * Ask Product module: given productIds → list of {productId, paymentMethodId, paymentMethodCode}.
     * Routing key: product.payment.method.mapping.lookup.request
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchProductPaymentMethodMappings(List<Long> productIds) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("requestId", UUID.randomUUID().toString());
            req.put("productIds", productIds);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));
            Object raw = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "product.payment.method.mapping.lookup.request",
                    req
            );

            if (raw instanceof Map) {
                Map<String, Object> resp = (Map<String, Object>) raw;
                if (Boolean.TRUE.equals(resp.get("found"))) {
                    return (List<Map<String, Object>>) resp.get("mappings");
                } else {
                    log.warn("Product payment method mapping lookup failed: {}", resp.get("errorMessage"));
                }
            }
        } catch (Exception e) {
            log.error("Error fetching product payment method mappings from product module", e);
        }
        return Collections.emptyList();
    }

    private PaymentMethodResponse toPaymentMethodResponse(PaymentMethodEntity entity) {
        return PaymentMethodResponse.builder()
                .uuid(entity.getUuid())
                .name(entity.getName())
                .code(entity.getCode())
                .type(entity.getType())
                .isActive(entity.getIsActive())
                .supportsRefund(entity.getSupportsRefund())
                .supportsCallback(entity.getSupportsCallback())
                .isCourierFeeFree(entity.getIsCourierFeeFree())
                .build();
    }

    // =========================================================
    // PRIVATE HELPERS — checkout response builders
    // =========================================================

    private CheckoutResponse buildCheckoutResponse(
            CartItemsLookupResponse cartResponse,
            CustomerAddressLookupResponse addressResponse,
            ShippingCalculationResponse shippingResponse,
            PaymentMethodEntity paymentMethod,
            String deliveryNotes) {

        List<CheckoutResponse.CheckoutItemDTO> items = cartResponse.getItems().stream()
                .map(item -> CheckoutResponse.CheckoutItemDTO.builder()
                        .itemUuid(item.getItemUuid())
                        .productUuid(item.getProductUuid())
                        .productTitle(item.getProductTitle())
                        .variantUuid(item.getVariantUuid())
                        .variantTitle(item.getVariantTitle())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .itemWeight(item.getItemWeight())
                        .imageUrl(item.getImageUrl())
                        .build())
                .collect(Collectors.toList());

        CheckoutResponse.DeliveryAddressDTO deliveryAddress = null;
        if (addressResponse != null && Boolean.TRUE.equals(addressResponse.getFound())) {
            deliveryAddress = CheckoutResponse.DeliveryAddressDTO.builder()
                    .fullName(addressResponse.getFullName())
                    .addressLine1(addressResponse.getAddressLine1())
                    .addressLine2(addressResponse.getAddressLine2())
                    .city(addressResponse.getCity())
                    .province(addressResponse.getProvince())
                    .postalCode(addressResponse.getPostalCode())
                    .phoneNumber(addressResponse.getPhoneNumber())
                    .email(addressResponse.getEmail())
                    .build();
        }

        CheckoutResponse.CourierDTO courier = CheckoutResponse.CourierDTO.builder()
                .courierId(shippingResponse.getCourierId())
                .courierName(shippingResponse.getCourierName())
                .shippingCost(shippingResponse.getShippingCost())
                .isFreeShipping(shippingResponse.getIsFree())
                .shippingBreakdown(shippingResponse.getBreakdown())
                .build();

        CheckoutResponse.PaymentMethodDTO paymentMethodDTO = CheckoutResponse.PaymentMethodDTO.builder()
                .paymentMethodId(paymentMethod.getId())
                .paymentMethodName(paymentMethod.getName())
                .paymentMethodCode(paymentMethod.getCode())
                .paymentMethodType(paymentMethod.getType().name())
                .build();

        BigDecimal subtotal      = cartResponse.getSubtotal()      != null ? cartResponse.getSubtotal()      : BigDecimal.ZERO;
        BigDecimal promoDiscount = cartResponse.getPromoDiscount()  != null ? cartResponse.getPromoDiscount() : BigDecimal.ZERO;
        BigDecimal shippingCost  = shippingResponse.getShippingCost() != null ? shippingResponse.getShippingCost() : BigDecimal.ZERO;
        BigDecimal total         = subtotal.subtract(promoDiscount).add(shippingCost);

        CheckoutResponse.PriceBreakdownDTO priceBreakdown = CheckoutResponse.PriceBreakdownDTO.builder()
                .subtotal(subtotal)
                .promoDiscount(promoDiscount)
                .shippingCost(shippingCost)
                .totalWeight(cartResponse.getTotalWeight())
                .total(total)
                .build();

        return CheckoutResponse.builder()
                .items(items)
                .deliveryAddress(deliveryAddress)
                .courier(courier)
                .paymentMethod(paymentMethodDTO)
                .priceBreakdown(priceBreakdown)
                .promoCode(cartResponse.getPromoCode())
                .deliveryNotes(deliveryNotes)
                .build();
    }

//    private void cacheCheckoutSummary(String customerUuid, String guestSessionToken, CheckoutResponse response) {
//        try {
//            String cacheKey = CHECKOUT_CACHE_PREFIX + (customerUuid != null ? customerUuid : guestSessionToken);
//            redisTemplate.opsForValue().set(cacheKey, response, CHECKOUT_CACHE_TTL);
//            log.debug("Checkout summary cached with key: {}", cacheKey);
//        } catch (Exception e) {
//            log.warn("Failed to cache checkout summary", e);
//        }
//    }
}
