package org.psint.beyosclothing.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.dto.external.*;
import org.psint.beyosclothing.modules.payment.service.PaymentCrossModuleLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Cross-Module Lookup Service Implementation for Payment Module
 * Uses RabbitMQ Request-Reply pattern for synchronous cross-module communication
 * ✅ MICROSERVICES-READY: Uses JSON serialization with explicit type handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCrossModuleLookupServiceImpl implements PaymentCrossModuleLookupService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchange;

    @Value("${app.rabbitmq.exchange.cart}")
    private String cartExchange;

    @Value("${app.rabbitmq.exchange.delivery}")
    private String deliveryExchange;

    private static final long LOOKUP_TIMEOUT_MS = 5000; // 5 seconds timeout

    @Override
    public CustomerAddressLookupResponse lookupCustomerAddress(Long addressId) {
        log.debug("Looking up customer address for address ID: {}", addressId);

        try {
            String requestId = UUID.randomUUID().toString();

            // Use shared DTO for RabbitMQ request
            org.psint.beyosclothing.shared.dto.CustomerAddressLookupRequest sharedRequest =
                    org.psint.beyosclothing.shared.dto.CustomerAddressLookupRequest.builder()
                    .addressId(addressId)
                    .requestId(requestId)
                    .build();

            Object response = rabbitTemplate.convertSendAndReceive(
                    customerExchange,
                    "customer.address.lookup.request",
                    sharedRequest
            );

            log.info("Received response from Customer module - Type: {}, isNull: {}",
                    response != null ? response.getClass().getName() : "null",
                    response == null);

            if (response == null) {
                log.warn("Null response from Customer module (RPC timeout or consumer error)");
                return null;
            }

            // ✅ Case 1: Response is already the shared DTO type (preferred path)
            if (response instanceof org.psint.beyosclothing.shared.dto.CustomerAddressLookupResponse) {
                org.psint.beyosclothing.shared.dto.CustomerAddressLookupResponse shared =
                        (org.psint.beyosclothing.shared.dto.CustomerAddressLookupResponse) response;
                log.info("Shared DTO address response - found: {}", shared.getFound());
                if (Boolean.TRUE.equals(shared.getFound())) {
                    return convertSharedToPaymentAddressDTO(shared);
                }
                return null;
            }

            // ✅ Case 2: Response is the Payment module's DTO (direct match)
            if (response instanceof CustomerAddressLookupResponse) {
                CustomerAddressLookupResponse addressResponse = (CustomerAddressLookupResponse) response;
                log.info("Payment DTO address response - found: {}", addressResponse.getFound());
                if (Boolean.TRUE.equals(addressResponse.getFound())) {
                    return addressResponse;
                }
                return null;
            }

            // ✅ Case 3: Response is Customer module's DTO (legacy __TypeId__ mismatch)
            if (response instanceof org.psint.beyosclothing.modules.customers.dto.external.CustomerAddressLookupResponse) {
                org.psint.beyosclothing.modules.customers.dto.external.CustomerAddressLookupResponse customerDto =
                        (org.psint.beyosclothing.modules.customers.dto.external.CustomerAddressLookupResponse) response;
                log.info("Customer module DTO address response (legacy) - found: {}", customerDto.getFound());
                if (Boolean.TRUE.equals(customerDto.getFound())) {
                    return convertCustomerDtoToPaymentAddressDTO(customerDto);
                }
                return null;
            }

            // ✅ Case 4: Fallback - attempt JSON round-trip conversion
            log.warn("Response type mismatch (type={}) - attempting manual conversion",
                    response.getClass().getName());
            CustomerAddressLookupResponse converted = convertToCustomerAddressLookupResponse(response);
            if (converted != null && Boolean.TRUE.equals(converted.getFound())) {
                return converted;
            }

            log.warn("Customer address not found for address ID: {}", addressId);
            return null;

        } catch (Exception e) {
            log.error("Error looking up customer address for address ID: {}", addressId, e);
            return null;
        }
    }

    /**
     * Convert shared DTO address response to Payment module's DTO
     */
    private CustomerAddressLookupResponse convertSharedToPaymentAddressDTO(
            org.psint.beyosclothing.shared.dto.CustomerAddressLookupResponse shared) {
        return CustomerAddressLookupResponse.builder()
                .requestId(shared.getRequestId())
                .found(shared.getFound())
                .addressId(shared.getAddressId())
                .customerId(shared.getCustomerId())
                .customerUuid(shared.getCustomerUuid())
                .fullName(shared.getFullName())
                .addressLine1(shared.getAddressLine1())
                .addressLine2(shared.getAddressLine2())
                .city(shared.getCity())
                .province(shared.getProvince())
                .postalCode(shared.getPostalCode())
                .phoneNumber(shared.getPhoneNumber())
                .country(shared.getCountry())
                .email(shared.getEmail())
                .customerType(shared.getCustomerType())
                .build();
    }

    /**
     * Convert Customer module's DTO response to Payment module's DTO (legacy fallback)
     */
    private CustomerAddressLookupResponse convertCustomerDtoToPaymentAddressDTO(
            org.psint.beyosclothing.modules.customers.dto.external.CustomerAddressLookupResponse customerDto) {
        return CustomerAddressLookupResponse.builder()
                .requestId(customerDto.getRequestId())
                .found(customerDto.getFound())
                .addressId(customerDto.getAddressId())
                .fullName(customerDto.getFullName())
                .phoneNumber(customerDto.getPhoneNumber())
                .email(customerDto.getEmail())
                .addressLine1(customerDto.getAddressLine1())
                .addressLine2(customerDto.getAddressLine2())
                .city(customerDto.getCity())
                .province(customerDto.getProvince())
                .postalCode(customerDto.getPostalCode())
                .country(customerDto.getCountry())
                .build();
    }

    @Override
    public CartItemsLookupResponse getCartItemsForCheckout(String customerUuid, String guestSessionToken, List<String> selectedItemUuids) {
        log.debug("Getting cart items for checkout - Customer UUID: {}, Guest Token: {}",
                customerUuid, guestSessionToken != null ? "***" : null);

        try {
            String requestId = UUID.randomUUID().toString();
            org.psint.beyosclothing.shared.dto.CartItemsLookupRequest request =
                    org.psint.beyosclothing.shared.dto.CartItemsLookupRequest.builder()
                    .requestId(requestId)
                    .customerUuid(customerUuid)
                    .guestSessionToken(guestSessionToken)
                    .selectedItemUuids(selectedItemUuids)
                    .build();

            log.debug("Sending cart checkout request to Cart module - Request ID: {}", requestId);

            Object rawResponse = rabbitTemplate.convertSendAndReceive(
                    cartExchange,
                    "cart.items.checkout.request",
                    request
            );

            log.info("Received response from Cart module - Type: {}, isNull: {}",
                    rawResponse != null ? rawResponse.getClass().getName() : "null",
                    rawResponse == null);

            if (rawResponse == null) {
                log.warn("Null response from Cart module (RPC timeout or consumer error)");
                return null;
            }

            // ✅ Case 1: Response is already the shared DTO type (preferred path)
            if (rawResponse instanceof org.psint.beyosclothing.shared.dto.CartItemsLookupResponse) {
                org.psint.beyosclothing.shared.dto.CartItemsLookupResponse shared =
                        (org.psint.beyosclothing.shared.dto.CartItemsLookupResponse) rawResponse;
                log.info("Shared DTO response - found: {}", shared.getFound());
                if (Boolean.TRUE.equals(shared.getFound())) {
                    return convertSharedToPaymentDTO(shared);
                }
                log.warn("Cart module returned found=false for customerUuid: {}", customerUuid);
                return null;
            }

            // ✅ Case 2: Response is the Payment module's DTO (direct match)
            if (rawResponse instanceof CartItemsLookupResponse) {
                CartItemsLookupResponse cartResponse = (CartItemsLookupResponse) rawResponse;
                log.info("Payment DTO response - found: {}", cartResponse.getFound());
                if (Boolean.TRUE.equals(cartResponse.getFound())) {
                    return cartResponse;
                }
                log.warn("Cart module returned found=false for customerUuid: {}", customerUuid);
                return null;
            }

            // ✅ Case 3: Response is Cart module's DTO (legacy __TypeId__ mismatch)
            if (rawResponse instanceof org.psint.beyosclothing.modules.cart.dto.external.CartItemsLookupResponse) {
                org.psint.beyosclothing.modules.cart.dto.external.CartItemsLookupResponse cartDto =
                        (org.psint.beyosclothing.modules.cart.dto.external.CartItemsLookupResponse) rawResponse;
                log.info("Cart module DTO response (legacy) - found: {}", cartDto.getFound());
                if (Boolean.TRUE.equals(cartDto.getFound())) {
                    return convertCartDtoToPaymentDTO(cartDto);
                }
                log.warn("Cart module returned found=false for customerUuid: {}", customerUuid);
                return null;
            }

            // ✅ Case 4: Fallback - attempt JSON round-trip conversion
            log.warn("Response type mismatch (type={}) - attempting manual conversion",
                    rawResponse.getClass().getName());
            CartItemsLookupResponse converted = convertToCartItemsLookupResponse(rawResponse);
            if (converted != null && Boolean.TRUE.equals(converted.getFound())) {
                return converted;
            }

            log.warn("Cart not found or no items selected for checkout");
            return null;

        } catch (Exception e) {
            log.error("Error getting cart items for checkout", e);
            return null;
        }
    }

    /**
     * Convert shared DTO response to Payment module's DTO
     */
    private CartItemsLookupResponse convertSharedToPaymentDTO(
            org.psint.beyosclothing.shared.dto.CartItemsLookupResponse shared) {
        List<CartItemsLookupResponse.CartItemInfo> items = null;
        if (shared.getItems() != null) {
            items = shared.getItems().stream()
                    .map(si -> CartItemsLookupResponse.CartItemInfo.builder()
                            .itemUuid(si.getItemUuid())
                            .productId(si.getProductId())
                            .productUuid(si.getProductUuid())
                            .productTitle(si.getProductTitle())
                            .variantId(si.getVariantId())
                            .variantUuid(si.getVariantUuid())
                            .variantTitle(si.getVariantTitle())
                            .quantity(si.getQuantity())
                            .unitPrice(si.getUnitPrice())
                            .totalPrice(si.getTotalPrice())
                            .itemWeight(si.getItemWeight())
                            .totalItemWeight(si.getTotalItemWeight())
                            .imageUrl(si.getImageUrl())
                            .availableStock(si.getAvailableStock())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }
        return CartItemsLookupResponse.builder()
                .requestId(shared.getRequestId())
                .found(shared.getFound())
                .cartId(shared.getCartId())
                .cartUuid(shared.getCartUuid())
                .items(items)
                .promoCode(shared.getPromoCode())
                .promoDiscount(shared.getPromoDiscount())
                .subtotal(shared.getSubtotal())
                .totalWeight(shared.getTotalWeight())
                .build();
    }

    /**
     * Convert Cart module's DTO response to Payment module's DTO (legacy fallback)
     */
    private CartItemsLookupResponse convertCartDtoToPaymentDTO(
            org.psint.beyosclothing.modules.cart.dto.external.CartItemsLookupResponse cartDto) {
        List<CartItemsLookupResponse.CartItemInfo> items = null;
        if (cartDto.getItems() != null) {
            items = cartDto.getItems().stream()
                    .map(ci -> CartItemsLookupResponse.CartItemInfo.builder()
                            .itemUuid(ci.getItemUuid())
                            .productId(ci.getProductId())
                            .productUuid(ci.getProductUuid())
                            .productTitle(ci.getProductTitle())
                            .variantId(ci.getVariantId())
                            .variantUuid(ci.getVariantUuid())
                            .variantTitle(ci.getVariantTitle())
                            .quantity(ci.getQuantity())
                            .unitPrice(ci.getUnitPrice())
                            .totalPrice(ci.getTotalPrice())
                            .itemWeight(ci.getItemWeight())
                            .totalItemWeight(ci.getTotalItemWeight())
                            .imageUrl(ci.getImageUrl())
                            .availableStock(ci.getAvailableStock())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }
        return CartItemsLookupResponse.builder()
                .requestId(cartDto.getRequestId())
                .found(cartDto.getFound())
                .cartId(cartDto.getCartId())
                .cartUuid(cartDto.getCartUuid())
                .items(items)
                .promoCode(cartDto.getPromoCode())
                .promoDiscount(cartDto.getPromoDiscount())
                .subtotal(cartDto.getSubtotal())
                .totalWeight(cartDto.getTotalWeight())
                .build();
    }

    @Override
    public ShippingCalculationResponse calculateShippingCost(ShippingCalculationRequest request) {
        log.debug("Calculating shipping cost - Courier ID: {}, Weight: {} kg, Customer Type: {}, Payment Method: {}",
                request.getCourierUuid(), request.getTotalWeight(), request.getCustomerType(), request.getPaymentMethodId());

        try {
            // Use shared DTO for RabbitMQ request
            org.psint.beyosclothing.shared.dto.ShippingCalculationRequest sharedRequest =
                    org.psint.beyosclothing.shared.dto.ShippingCalculationRequest.builder()
                    .requestId(request.getRequestId())
                    .courierUuid(request.getCourierUuid())
                    .totalWeight(request.getTotalWeight())
                    .customerType(request.getCustomerType())
                    .paymentMethodId(request.getPaymentMethodId())
                    .build();

            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "delivery.shipping.calculate.request",
                    sharedRequest
            );

            log.info("Received response from Delivery module - Type: {}, isNull: {}",
                    response != null ? response.getClass().getName() : "null",
                    response == null);

            if (response == null) {
                log.warn("Null response from Delivery module (RPC timeout or consumer error)");
                return null;
            }

            // ✅ Case 1: Response is already the shared DTO type (preferred path)
            if (response instanceof org.psint.beyosclothing.shared.dto.ShippingCalculationResponse) {
                org.psint.beyosclothing.shared.dto.ShippingCalculationResponse shared =
                        (org.psint.beyosclothing.shared.dto.ShippingCalculationResponse) response;
                log.info("Shared DTO shipping response - found: {}", shared.getFound());
                if (!Boolean.TRUE.equals(shared.getFound())) {
                    log.warn("Delivery module returned found=false: {}", shared.getErrorMessage());
                }
                // Return the DTO in both cases so the caller can read the actual reason (errorMessage).
                return convertSharedToPaymentShippingDTO(shared);
            }

            // ✅ Case 2: Response is the Payment module's DTO (direct match)
            if (response instanceof ShippingCalculationResponse) {
                ShippingCalculationResponse shippingResponse = (ShippingCalculationResponse) response;
                log.info("Payment DTO shipping response - found: {}", shippingResponse.getFound());
                if (!Boolean.TRUE.equals(shippingResponse.getFound())) {
                    log.warn("Delivery module returned found=false: {}", shippingResponse.getErrorMessage());
                }
                // Return the DTO in both cases so the caller can read the actual reason (errorMessage).
                return shippingResponse;
            }

            // ✅ Case 3: Response is Delivery module's DTO (legacy __TypeId__ mismatch)
            if (response instanceof org.psint.beyosclothing.modules.delivery.dto.external.ShippingCalculationResponse) {
                org.psint.beyosclothing.modules.delivery.dto.external.ShippingCalculationResponse deliveryDto =
                        (org.psint.beyosclothing.modules.delivery.dto.external.ShippingCalculationResponse) response;
                log.info("Delivery module DTO shipping response (legacy) - found: {}", deliveryDto.getFound());
                if (!Boolean.TRUE.equals(deliveryDto.getFound())) {
                    log.warn("Delivery module returned found=false: {}", deliveryDto.getErrorMessage());
                }
                // Return the DTO in both cases so the caller can read the actual reason (errorMessage).
                return convertDeliveryDtoToPaymentDTO(deliveryDto);
            }

            // ✅ Case 4: Fallback - attempt JSON round-trip conversion
            log.warn("Response type mismatch (type={}) - attempting manual conversion",
                    response.getClass().getName());
            ShippingCalculationResponse converted = convertToShippingCalculationResponse(response);
            if (converted != null) {
                if (!Boolean.TRUE.equals(converted.getFound())) {
                    log.warn("Shipping calculation returned found=false for courier ID: {} - Reason: {}",
                            request.getCourierUuid(), converted.getErrorMessage());
                }
                // Return the DTO so the caller can read the actual reason (errorMessage).
                return converted;
            }

            log.warn("Shipping calculation failed for courier ID: {} - unable to parse response", request.getCourierUuid());
            return null;

        } catch (Exception e) {
            log.error("Error calculating shipping cost", e);
            return null;
        }
    }

    /**
     * Convert shared DTO shipping response to Payment module's DTO
     */
    private ShippingCalculationResponse convertSharedToPaymentShippingDTO(
            org.psint.beyosclothing.shared.dto.ShippingCalculationResponse shared) {
        return ShippingCalculationResponse.builder()
                .requestId(shared.getRequestId())
                .found(shared.getFound())
                .courierId(shared.getCourierId())
                .courierName(shared.getCourierName())
                .shippingCost(shared.getShippingCost())
                .totalWeight(shared.getTotalWeight())
                .isFree(shared.getIsFree())
                .breakdown(shared.getBreakdown())
                .errorMessage(shared.getErrorMessage())
                .build();
    }

    /**
     * Convert Delivery module's DTO response to Payment module's DTO (legacy fallback)
     */
    private ShippingCalculationResponse convertDeliveryDtoToPaymentDTO(
            org.psint.beyosclothing.modules.delivery.dto.external.ShippingCalculationResponse deliveryDto) {
        return ShippingCalculationResponse.builder()
                .requestId(deliveryDto.getRequestId())
                .found(deliveryDto.getFound())
                .courierId(deliveryDto.getCourierId())
                .courierName(deliveryDto.getCourierName())
                .shippingCost(deliveryDto.getShippingCost())
                .totalWeight(deliveryDto.getTotalWeight())
                .isFree(deliveryDto.getIsFree())
                .breakdown(deliveryDto.getBreakdown())
                .errorMessage(deliveryDto.getErrorMessage())
                .build();
    }

    /**
     * ✅ Fallback conversion method for microservices compatibility
     * Converts raw response object to CartItemsLookupResponse using JSON
     */
    private CartItemsLookupResponse convertToCartItemsLookupResponse(Object rawResponse) {
        try {
            // Use Jackson to re-serialize and deserialize
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(rawResponse);
            return mapper.readValue(json, CartItemsLookupResponse.class);
        } catch (Exception e) {
            log.error("Failed to convert response to CartItemsLookupResponse", e);
            return null;
        }
    }

    /**
     * ✅ Fallback conversion method for CustomerAddressLookupResponse
     * Converts raw response object to CustomerAddressLookupResponse using JSON
     */
    private CustomerAddressLookupResponse convertToCustomerAddressLookupResponse(Object rawResponse) {

        try {
            log.info("Debugging CustomerAddressLookupResponse conversion - Raw response type: {}, content: {}",
                    rawResponse != null ? rawResponse.getClass().getName() : "null",
                    rawResponse != null ? rawResponse.toString() : "null");
            // Use Jackson to re-serialize and deserialize
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(rawResponse);
            return mapper.readValue(json, CustomerAddressLookupResponse.class);
        } catch (Exception e) {
            log.error("Failed to convert response to CustomerAddressLookupResponse", e);
            return null;
        }
    }

    /**
     * ✅ Fallback conversion method for ShippingCalculationResponse
     * Converts raw response object to ShippingCalculationResponse using JSON
     */
    private ShippingCalculationResponse convertToShippingCalculationResponse(Object rawResponse) {
        try {
            log.info("Debugging ShippingCalculationResponse conversion - Raw response type: {}, content: {}",
                    rawResponse != null ? rawResponse.getClass().getName() : "null",
                    rawResponse != null ? rawResponse.toString() : "null");
            // Use Jackson to re-serialize and deserialize
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(rawResponse);
            return mapper.readValue(json, ShippingCalculationResponse.class);
        } catch (Exception e) {
            log.error("Failed to convert response to ShippingCalculationResponse", e);
            return null;
        }
    }
}
