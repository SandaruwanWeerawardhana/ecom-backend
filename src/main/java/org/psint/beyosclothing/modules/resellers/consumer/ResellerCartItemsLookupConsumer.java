package org.psint.beyosclothing.modules.resellers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.entity.ResellerCartItem;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartItemRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reseller Cart Items Lookup Consumer
 * Handles RPC requests from the order module to fetch cart items by cartId.
 * Used by Admin View Order API to get reseller order items.
 *
 * Request keys:  cartId
 * Response keys: found, cartId, items (list of maps)
 * Each item map:  productId, variantId, productName, variantName,
 *                 baseUnitPrice, overrideUnitPrice, effectivePrice, quantity,
 *                 totalPrice, marginAmount
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerCartItemsLookupConsumer {

    private final ResellerCartItemRepository cartItemRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.reseller-cart-items-lookup-request:reseller.cart.items.lookup.request}")
    @Transactional(value = "resellerTransactionManager", readOnly = true)
    public Map<String, Object> handleCartItemsLookup(Map<String, Object> request) {
        log.debug("[RESELLER CART ITEMS LOOKUP] Received request: {}", request);

        Map<String, Object> response = new HashMap<>();

        try {
            Long cartId = null;
            Object cartIdObj = request != null ? request.get("cartId") : null;
            if (cartIdObj instanceof Number) {
                cartId = ((Number) cartIdObj).longValue();
            }

            if (cartId == null) {
                log.warn("[RESELLER CART ITEMS LOOKUP] Missing cartId in request");
                response.put("found", false);
                response.put("error", "cartId is required");
                return response;
            }

            List<ResellerCartItem> items = cartItemRepository.findByCartId(cartId);

            List<Map<String, Object>> itemMaps = items.stream().map(item -> {
                Map<String, Object> m = new HashMap<>();
                m.put("productId", item.getProductId());
                m.put("variantId", item.getVariantId());
                m.put("productName", item.getProductName());
                m.put("variantName", item.getVariantName());
                m.put("baseUnitPrice", item.getBaseUnitPrice());
                m.put("overrideUnitPrice", item.getOverrideUnitPrice());
                m.put("effectivePrice", item.getEffectivePrice());
                m.put("quantity", item.getQuantity());
                m.put("totalPrice", item.getTotalPrice());
                m.put("marginAmount", item.getMarginAmount());
                return m;
            }).collect(Collectors.toList());

            response.put("found", true);
            response.put("cartId", cartId);
            response.put("items", itemMaps);

            log.debug("[RESELLER CART ITEMS LOOKUP] Returning {} items for cartId={}", itemMaps.size(), cartId);
            return response;

        } catch (Exception e) {
            log.error("[RESELLER CART ITEMS LOOKUP] Error processing request", e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
            return response;
        }
    }
}

