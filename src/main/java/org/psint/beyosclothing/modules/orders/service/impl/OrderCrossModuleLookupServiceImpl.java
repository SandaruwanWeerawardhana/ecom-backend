package org.psint.beyosclothing.modules.orders.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.dto.external.*;
import org.psint.beyosclothing.modules.orders.service.OrderCrossModuleLookupService;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentMethodLookupRequest;
import org.psint.beyosclothing.shared.dto.CustomerAddressLookupRequest;
import org.psint.beyosclothing.shared.dto.CustomerLookupRequest;
import org.psint.beyosclothing.shared.dto.ShippingCalculationRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-Module Lookup Service Implementation for Order Module
 * Uses RabbitMQ Request-Reply pattern for synchronous cross-module communication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCrossModuleLookupServiceImpl implements OrderCrossModuleLookupService {

    private static final int INVENTORY_DECREASE_ATTEMPTS = 3;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.cart}")
    private String cartExchange;

    @Value("${app.rabbitmq.queue.cart-items-clear-request:cart.items.clear.request.queue}")
    private String cartItemsClearRequestQueue;

    @Value("${app.rabbitmq.exchange.customer}")
    private String customerExchange;

    @Value("${app.rabbitmq.exchange.payment}")
    private String paymentExchange;

    @Value("${app.rabbitmq.exchange.delivery}")
    private String deliveryExchange;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    @Override
    public CartItemsForOrderResponse getCartItemsForOrder(String customerUuid, String guestSessionToken, List<String> selectedItemUuids) {
        log.debug("Getting cart items for order - Customer UUID: {}, Guest Token: {}",
                customerUuid, guestSessionToken != null ? "***" : null);

        try {
            String requestId = UUID.randomUUID().toString();

            // Using the existing CartItemsLookupRequest structure from cart module
            Object request = objectMapper.convertValue(
                objectMapper.createObjectNode()
                    .put("requestId", requestId)
                    .put("customerUuid", customerUuid)
                    .put("guestSessionToken", guestSessionToken)
                    .set("selectedItemUuids", objectMapper.valueToTree(selectedItemUuids)),
                Object.class
            );

            Object response = rabbitTemplate.convertSendAndReceive(
                    cartExchange,
                    "cart.items.checkout.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, CartItemsForOrderResponse.class);
            }

            log.warn("No response from cart module");
            return null;

        } catch (Exception e) {
            log.error("Error getting cart items for order", e);
            return null;
        }
    }

    @Override
    public CustomerLookupResponse lookupCustomer(String customerUuid) {
        log.debug("Looking up customer by UUID: {}", customerUuid);

        try {
            CustomerLookupRequest request = CustomerLookupRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .customerUuid(customerUuid)
                    .build();

            Object response = rabbitTemplate.convertSendAndReceive(
                    customerExchange,
                    "customer.lookup.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, CustomerLookupResponse.class);
            }

            log.error("No reply from customer module for UUID: {} - the lookup request was not answered", customerUuid);
            return null;

        } catch (Exception e) {
            log.error("Error looking up customer", e);
            return null;
        }
    }

    @Override
    public CustomerAddressResponse lookupCustomerAddress(Long customerId, Long addressId) {
        log.debug("Looking up customer address - Customer ID: {}, Address ID: {}", customerId, addressId);

        try {
            CustomerAddressLookupRequest request = CustomerAddressLookupRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .customerId(customerId)
                    .addressId(addressId)
                    .build();

            Object response = rabbitTemplate.convertSendAndReceive(
                    customerExchange,
                    "customer.address.lookup.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, CustomerAddressResponse.class);
            }

            log.error("No reply from customer module for address lookup - Customer ID: {}, Address ID: {}",
                    customerId, addressId);
            return null;

        } catch (Exception e) {
            log.error("Error looking up customer address", e);
            return null;
        }
    }

    @Override
    public PaymentMethodLookupResponse lookupPaymentMethod(Long paymentMethodId) {
        log.debug("Looking up payment method: {}", paymentMethodId);

        try {
            PaymentMethodLookupRequest request = PaymentMethodLookupRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .paymentMethodId(paymentMethodId)
                    .build();

            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange,
                    "payment.method.lookup.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, PaymentMethodLookupResponse.class);
            }

            log.error("No reply from payment module for payment method: {}", paymentMethodId);
            return null;

        } catch (Exception e) {
            log.error("Error looking up payment method", e);
            return null;
        }
    }

    @Override
    public ShippingCostResponse calculateShippingCost(String courierUuid, BigDecimal totalWeight,
                                                      Long paymentMethodId, String customerType) {
        log.debug("Calculating shipping cost - Courier UUID: {}, Weight: {}, Payment Method: {}",
                courierUuid, totalWeight, paymentMethodId);

        try {
            ShippingCalculationRequest request = ShippingCalculationRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .courierUuid(courierUuid)
                    .totalWeight(totalWeight)
                    .paymentMethodId(paymentMethodId)
                    .customerType(customerType)
                    .build();

            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "delivery.shipping.calculate.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, ShippingCostResponse.class);
            }

            log.error("No reply from delivery module for shipping calculation, courier UUID: {}", courierUuid);
            return null;

        } catch (Exception e) {
            log.error("Error calculating shipping cost", e);
            return null;
        }
    }

    @Override
    public ShipmentCreationResponse createShipment(ShipmentCreationRequest request) {
        log.debug("Creating shipment for order: {}", request.getOrderId());

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "delivery.shipment.create.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, ShipmentCreationResponse.class);
            }

            log.warn("Shipment creation failed for order: {}", request.getOrderId());
            return ShipmentCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("No response from delivery module")
                    .build();

        } catch (Exception e) {
            log.error("Error creating shipment", e);
            return ShipmentCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentRequestCreationResponse createPaymentRequest(PaymentRequestCreationRequest request) {
        log.debug("Creating payment request for order: {}", request.getOrderId());

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange,
                    "payment.request.create.request",
                    request
            );

            if (response != null) {
                return objectMapper.convertValue(response, PaymentRequestCreationResponse.class);
            }

            log.warn("Payment request creation failed for order: {}", request.getOrderId());
            return PaymentRequestCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("No response from payment module")
                    .build();

        } catch (Exception e) {
            log.error("Error creating payment request", e);
            return PaymentRequestCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public InventoryUpdateResponse decreaseInventoryStock(List<InventoryUpdateRequest.InventoryItem> items) {
        log.debug("Decreasing inventory stock for {} items", items.size());

        String requestId = UUID.randomUUID().toString();
        try {
            InventoryUpdateRequest request = InventoryUpdateRequest.builder()
                    .requestId(requestId)
                    .items(items)
                    .build();


            for (int attempt = 1; attempt <= INVENTORY_DECREASE_ATTEMPTS; attempt++) {
                Object response = rabbitTemplate.convertSendAndReceive(
                        inventoryExchange,
                        "inventory.stock.decrease.request",
                        request
                );

                if (response != null) {
                    return objectMapper.convertValue(response, InventoryUpdateResponse.class);
                }
                log.warn("No reply from inventory module for stock decrease request {} (attempt {}/{})",
                        requestId, attempt, INVENTORY_DECREASE_ATTEMPTS);
            }

            return InventoryUpdateResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .errorMessage("No response from inventory module")
                    .build();

        } catch (Exception e) {
            log.error("Error decreasing inventory stock", e);
            return InventoryUpdateResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public CartClearResponse clearCartItems(Long cartId, List<String> itemUuids) {
        log.debug("Clearing cart items - Cart ID: {}, Items: {}", cartId, itemUuids != null ? itemUuids.size() : "all");

        try {
            String requestId = UUID.randomUUID().toString();
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", requestId);
            request.put("cartId", cartId);
            request.put("itemUuids", itemUuids);

            Object response = sendCartClearRequest(request);

            if (response != null) {
                return objectMapper.convertValue(response, CartClearResponse.class);
            }

            log.warn("Cart clear failed for cart ID: {}", cartId);
            return CartClearResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .errorMessage("No response from cart module")
                    .build();

        } catch (Exception e) {
            log.error("Error clearing cart items", e);
            return CartClearResponse.builder()
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    private Object sendCartClearRequest(Map<String, Object> request) {
        Object response = rabbitTemplate.convertSendAndReceive(
                cartExchange,
                "cart.items.clear.request",
                request
        );

        if (response != null) {
            return response;
        }

        log.warn("No response via cart exchange route. Retrying cart clear via default exchange queue route. cartClearQueue={}",
                cartItemsClearRequestQueue);

        return rabbitTemplate.convertSendAndReceive(
                "",
                cartItemsClearRequestQueue,
                request
        );
    }
}

