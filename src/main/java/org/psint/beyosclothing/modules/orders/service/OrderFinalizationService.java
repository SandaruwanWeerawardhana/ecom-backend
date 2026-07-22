package org.psint.beyosclothing.modules.orders.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.dto.request.InventoryUpdateRequest;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderItemEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the stock and cart side effects that finalize an order once its online payment succeeds.
 * These are the same actions taken inline at placement time for offline orders (e.g. Cash on
 * Delivery); for online payments they are intentionally deferred until the payment-success callback
 * so an abandoned checkout never consumes stock or empties the customer's cart.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFinalizationService {

    private static final String INVENTORY_DECREASE_ROUTING_KEY = "inventory.stock.decrease.request";
    private static final String CART_CLEAR_ROUTING_KEY = "cart.items.clear.request";

    private final OrderItemRepository orderItemRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.inventory:beyos.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.exchange.cart}")
    private String cartExchange;

    /**
     * Decreases inventory for every item of the paid order and clears the cart it originated from.
     * Failures are logged rather than thrown: the payment has already been captured, so finalization
     * must not be reversed here.
     */
    public void finalizePaidOrder(OrderEntity order) {
        decreaseInventory(order);
        clearCart(order.getCartId());
    }

    private void decreaseInventory(OrderEntity order) {
        List<OrderItemEntity> orderItems = orderItemRepository.findByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            log.warn("No order items found to decrease inventory for paid order {}", order.getId());
            return;
        }

        List<InventoryUpdateRequest.InventoryItem> inventoryItems = orderItems.stream()
                .map(item -> InventoryUpdateRequest.InventoryItem.builder()
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        InventoryUpdateRequest inventoryRequest = InventoryUpdateRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .items(inventoryItems)
                .build();

        try {
            rabbitTemplate.convertAndSend(inventoryExchange, INVENTORY_DECREASE_ROUTING_KEY, inventoryRequest);
            log.info("Requested inventory decrease for {} item(s) of paid order {}", inventoryItems.size(), order.getId());
        } catch (Exception e) {
            log.error("Failed to request inventory decrease for paid order {}", order.getId(), e);
        }
    }

    private void clearCart(Long cartId) {
        if (cartId == null) {
            log.warn("Cannot clear cart for paid order - cart ID is missing");
            return;
        }

        Map<String, Object> clearRequest = new HashMap<>();
        clearRequest.put("requestId", UUID.randomUUID().toString());
        clearRequest.put("cartId", cartId);
        // itemUuids omitted: the cart module clears the whole cart that produced this order.

        try {
            rabbitTemplate.convertAndSend(cartExchange, CART_CLEAR_ROUTING_KEY, clearRequest);
            log.info("Requested cart clear for cartId {} of paid order", cartId);
        } catch (Exception e) {
            log.error("Failed to request cart clear for cartId {} of paid order", cartId, e);
        }
    }
}
