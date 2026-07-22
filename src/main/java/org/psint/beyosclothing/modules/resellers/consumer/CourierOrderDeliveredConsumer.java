package org.psint.beyosclothing.modules.resellers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.entity.ResellerCartItem;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartItemRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for order delivery completed events from order module
 * Handles reseller wallet credit when orders are delivered
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CourierOrderDeliveredConsumer {

    private final ResellerCartItemRepository cartItemRepository;
    private final ResellerWalletService walletService;

    /**
     * Handle order delivered event
     * Calculates total margin from cart items and credits reseller wallet
     * Event format: {
     *   orderId: Long,
     *   resellerId: Long,
     *   cartId: Long,
     *   shipmentUuid: String,
     *   waybillId: String,
     *   deliveredAt: LocalDateTime,
     *   eventSource: String,
     *   timestamp: Long
     * }
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.reseller-order-delivered:reseller.order.delivered.cart.lookup}")
    @Transactional(value = "resellerTransactionManager")
    public void handleOrderDelivered(Map<String, Object> event) {
        try {
            log.info("💳 Received order delivered event for reseller wallet credit: {}", event);

            Long resellerId = null;
            Long cartId = null;
            Long orderId = null;

            Object resellerIdObj = event.get("resellerId");
            Object cartIdObj = event.get("cartId");
            Object orderIdObj = event.get("orderId");

            if (resellerIdObj instanceof Number) {
                resellerId = ((Number) resellerIdObj).longValue();
            }
            if (cartIdObj instanceof Number) {
                cartId = ((Number) cartIdObj).longValue();
            }
            if (orderIdObj instanceof Number) {
                orderId = ((Number) orderIdObj).longValue();
            }

            if (resellerId == null || cartId == null) {
                log.warn("⚠️ Missing resellerId or cartId in order delivered event");
                return;
            }

            log.info("💰 Processing wallet credit: ResellerId={}, CartId={}, OrderId={}",
                    resellerId, cartId, orderId);

            // Fetch all active cart items for this cart
            List<ResellerCartItem> cartItems = cartItemRepository.findByCartIdAndIsActiveTrue(cartId);

            if (cartItems.isEmpty()) {
                log.warn("⚠️ No active cart items found for cart: {}", cartId);
                return;
            }

            // Calculate total margin from all cart items
            BigDecimal totalMargin = cartItems.stream()
                    .map(ResellerCartItem::getMarginAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.info("📊 Cart Items Found: {}, Total Margin: {}", cartItems.size(), totalMargin);

            if (totalMargin.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ No margin to credit for cart: {}", cartId);
                return;
            }

            // Credit the wallet
            walletService.creditSaleProfit(resellerId, totalMargin, orderId, null);
            log.info("✅ Credited reseller {} wallet with margin: {} for order: {}",
                    resellerId, totalMargin, orderId);

        } catch (Exception e) {
            log.error("❌ Error handling order delivered event for reseller wallet: {}", e.getMessage(), e);
        }
    }
}
