package org.psint.beyosclothing.modules.cart.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.entity.CartEntity;
import org.psint.beyosclothing.modules.cart.entity.CartItemEntity;
import org.psint.beyosclothing.modules.cart.repository.CartItemRepository;
import org.psint.beyosclothing.modules.cart.repository.CartRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RabbitMQ Consumer: Cart lookup by cartUuid for payment method resolution.
 *
 * Called by Payment module to get cart product IDs so it can check
 * allowed payment methods per product.
 *
 * Request:  { requestId, cartUuid }
 * Response: { requestId, found, cartId, cartUuid, productIds: [1, 2, ...], errorMessage }
 *
 * NO cross-module DTO dependencies — uses Map<String, Object>.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartPaymentMethodLookupConsumer {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.cart-payment-method-lookup-request:cart.payment.method.lookup.request.queue}")
    public Map<String, Object> handleCartPaymentMethodLookup(Map<String, Object> request) {
        String requestId = request != null ? (String) request.get("requestId") : null;
        String cartUuid  = request != null ? (String) request.get("cartUuid")  : null;

        log.info("📦 [CART PAYMENT METHOD LOOKUP] requestId={}, cartUuid={}", requestId, cartUuid);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            if (cartUuid == null || cartUuid.isBlank()) {
                response.put("found", false);
                response.put("errorMessage", "cartUuid is required");
                return response;
            }

            CartEntity cart = cartRepository.findByUuid(cartUuid).orElse(null);
            if (cart == null) {
                log.warn("[CART PAYMENT METHOD LOOKUP] Cart not found - UUID: {}", cartUuid);
                response.put("found", false);
                response.put("errorMessage", "Cart not found for UUID: " + cartUuid);
                return response;
            }

            List<CartItemEntity> items = cartItemRepository.findByCartIdAndIsActiveTrue(cart.getId());
            if (items == null || items.isEmpty()) {
                log.warn("[CART PAYMENT METHOD LOOKUP] Cart is empty - UUID: {}", cartUuid);
                response.put("found", false);
                response.put("errorMessage", "Cart is empty");
                return response;
            }

            // Collect distinct product IDs from cart items
            List<Long> productIds = items.stream()
                    .map(CartItemEntity::getProductId)
                    .distinct()
                    .collect(Collectors.toList());

            response.put("found", true);
            response.put("cartId", cart.getId());
            response.put("cartUuid", cart.getUuid());
            response.put("productIds", productIds);

            log.info("[CART PAYMENT METHOD LOOKUP] Found {} distinct product IDs for cartUuid={}", productIds.size(), cartUuid);
            return response;

        } catch (Exception e) {
            log.error("[CART PAYMENT METHOD LOOKUP] Error - requestId={}, cartUuid={}", requestId, cartUuid, e);
            response.put("found", false);
            response.put("errorMessage", "Error: " + e.getMessage());
            return response;
        }
    }
}

