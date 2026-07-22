package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductPriceChangedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.products.events.VariantCreatedEvent;
import org.psint.beyosclothing.modules.products.service.ProductEventPublisherService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Product Event Publisher Service Implementation
 * Publishes product events to RabbitMQ for cross-module communication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisherServiceImpl implements ProductEventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    @Override
    public void publishProductCreated(ProductCreatedEvent event) {
        log.info("Publishing ProductCreatedEvent for product: {}", event.getUuid());
        try {
            rabbitTemplate.convertAndSend(
                    productExchange,
                    "product.created",
                    event
            );
            log.info("Successfully published ProductCreatedEvent for product: {}", event.getUuid());
        } catch (Exception e) {
            log.error("Error publishing ProductCreatedEvent for product: {}", event.getUuid(), e);
        }
    }

    @Override
    public void publishProductUpdated(ProductUpdatedEvent event) {
        log.info("Publishing ProductUpdatedEvent for product: {}", event.getUuid());
        try {
            rabbitTemplate.convertAndSend(
                    productExchange,
                    "product.updated",
                    event
            );
            log.info("Successfully published ProductUpdatedEvent for product: {}", event.getUuid());
        } catch (Exception e) {
            log.error("Error publishing ProductUpdatedEvent for product: {}", event.getUuid(), e);
        }
    }

    @Override
    public void publishProductDeleted(Long productId, String uuid) {
        log.info("Publishing ProductDeletedEvent for product: {}", uuid);
        try {
            rabbitTemplate.convertAndSend(
                    productExchange,
                    "product.deleted",
                    new ProductDeletedEvent(productId, uuid)
            );
            log.info("Successfully published ProductDeletedEvent for product: {}", uuid);
        } catch (Exception e) {
            log.error("Error publishing ProductDeletedEvent for product: {}", uuid, e);
        }
    }

    @Override
    public void publishProductPriceChanged(ProductPriceChangedEvent event) {
        log.info("Publishing ProductPriceChangedEvent for product: {}", event.getProductId());
        try {
            rabbitTemplate.convertAndSend(
                    productExchange,
                    "product.price.changed",
                    event
            );
            log.info("Successfully published ProductPriceChangedEvent for product: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error publishing ProductPriceChangedEvent for product: {}", event.getProductId(), e);
        }
    }


    @Override
    public void publishVariantCreated(VariantCreatedEvent event) {
        log.info("Publishing VariantCreatedEvent for variant: {} of product: {}", event.getUuid(), event.getProductId());
        try {
            rabbitTemplate.convertAndSend(
                    productExchange,
                    "variant.created",
                    event
            );
            log.info("Successfully published VariantCreatedEvent for variant: {}", event.getUuid());
        } catch (Exception e) {
            log.error("Error publishing VariantCreatedEvent for variant: {}", event.getUuid(), e);
        }
    }

    /**
     * Simple Product Deleted Event
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ProductDeletedEvent implements java.io.Serializable {
        private Long productId;
        private String uuid;
    }
}

