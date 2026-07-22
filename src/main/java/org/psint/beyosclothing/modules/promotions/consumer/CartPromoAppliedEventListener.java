package org.psint.beyosclothing.modules.promotions.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.events.CartPromoAppliedEvent;
import org.psint.beyosclothing.modules.promotions.entity.PromotionUsage;
import org.psint.beyosclothing.modules.promotions.repository.PromotionUsageRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Cart Promo Applied Event Listener
 * Listens to promo code application events from Cart module
 * Tracks promotion usage for analytics and limit enforcement
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartPromoAppliedEventListener {

    private final PromotionUsageRepository promotionUsageRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.cart-promo-applied:cart.promo.applied.queue}")
    public void handleCartPromoAppliedEvent(CartPromoAppliedEvent event) {
        log.info("Received cart promo applied event - Cart UUID: {}, Promo Code: {}, Discount: {}",
                event.getCartUuid(), event.getPromoCode(), event.getDiscountAmount());

        try {
            // Record promotion usage
            PromotionUsage usage = PromotionUsage.builder()
                    .promotionId(event.getPromoCodeId())
                    .customerId(event.getCustomerId())
                    .orderId(null) // Will be updated when order is created
                    .usedAt(event.getAppliedAt())
                    .isActive(true)
                    .build();

            promotionUsageRepository.save(usage);

            log.info("Promotion usage recorded successfully - Promo ID: {}, Customer ID: {}, Cart ID: {}",
                    event.getPromoCodeId(), event.getCustomerId(), event.getCartId());

        } catch (Exception e) {
            log.error("Error processing cart promo applied event - Cart UUID: {}, Promo Code: {}",
                    event.getCartUuid(), event.getPromoCode(), e);
            // Don't throw exception - this is an async event, we don't want to break the cart flow
        }
    }
}

