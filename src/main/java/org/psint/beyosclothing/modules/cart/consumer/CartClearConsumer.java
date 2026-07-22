package org.psint.beyosclothing.modules.cart.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.CartClearResponse;
import org.psint.beyosclothing.modules.cart.entity.CartItemEntity;
import org.psint.beyosclothing.modules.cart.repository.CartItemRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Consumer for cart clearing requests from Order module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartClearConsumer {

    private final CartItemRepository cartItemRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.cart-items-clear-request:cart.items.clear.request.queue}")
    @Transactional("cartTransactionManager")
    public CartClearResponse handleCartClearRequest(Map<String, Object> request) {
        String requestId = getString(request, "requestId");
        Long cartId = getLong(request, "cartId");
        List<String> itemUuids = getStringList(request, "itemUuids");

        log.info("Received cart clear request - Request ID: {}, Cart ID: {}, Items: {}",
                requestId, cartId, itemUuids != null ? itemUuids.size() : "all");

        try {
            if (cartId == null) {
                return CartClearResponse.builder()
                        .requestId(requestId)
                        .success(false)
                        .itemsCleared(0)
                        .errorMessage("Cart ID is required")
                        .build();
            }

            int itemsCleared = 0;

            if (itemUuids != null && !itemUuids.isEmpty()) {
                // Clear specific items by UUID
                List<CartItemEntity> items = cartItemRepository.findByCartIdAndUuidInAndIsActiveTrue(
                        cartId, itemUuids);

                for (CartItemEntity item : items) {
                    item.setIsActive(false);
                    item.setDateUpdated(LocalDateTime.now());
                }

                cartItemRepository.saveAll(items);
                itemsCleared = items.size();

                log.info("Cleared {} specific cart items - Cart ID: {}", itemsCleared, cartId);
            } else {
                // Clear all items in cart
                List<CartItemEntity> items = cartItemRepository.findByCartIdAndIsActiveTrue(cartId);

                for (CartItemEntity item : items) {
                    item.setIsActive(false);
                    item.setDateUpdated(LocalDateTime.now());
                }

                cartItemRepository.saveAll(items);
                itemsCleared = items.size();

                log.info("Cleared all {} cart items - Cart ID: {}", itemsCleared, cartId);
            }

            return CartClearResponse.builder()
                    .requestId(requestId)
                    .success(true)
                    .itemsCleared(itemsCleared)
                    .build();

        } catch (Exception e) {
            log.error("Error clearing cart items - Request ID: {}", requestId, e);
            return CartClearResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .itemsCleared(0)
                    .errorMessage("Error clearing cart: " + e.getMessage())
                    .build();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }
}
