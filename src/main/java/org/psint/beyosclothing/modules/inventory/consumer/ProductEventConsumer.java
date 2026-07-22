package org.psint.beyosclothing.modules.inventory.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.service.InventoryService;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.products.events.VariantCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Product Event Consumer
 * Listens to product events from RabbitMQ and updates inventory accordingly
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final InventoryService inventoryService;

    /**
     * Handle Product Created Event
     * Initialize stock records for new product
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue.product-created}")
    public void handleProductCreated(ProductCreatedEvent event) {
        log.info("Received ProductCreatedEvent for product: {} (UUID: {})", event.getProductId(), event.getUuid());

        try {
            inventoryService.initializeProductStock(event);
            log.info("Successfully initialized stock for product: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error handling ProductCreatedEvent for product: {}", event.getProductId(), e);
        }
    }

    /**
     * Handle Variant Created Event
     * Initialize stock records for new variant with initial quantity
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue.variant-created}")
    public void handleVariantCreated(VariantCreatedEvent event) {
        log.info("Received VariantCreatedEvent for variant: {} (Product: {})", event.getVariantId(), event.getProductId());

        try {
            inventoryService.initializeVariantStock(event);
            log.info("Successfully initialized stock for variant: {} with quantity: {}",
                     event.getVariantId(), event.getInitialStockQuantity());
        } catch (Exception e) {
            log.error("Error handling VariantCreatedEvent for variant: {}", event.getVariantId(), e);
        }
    }

    /**
     * Handle Product Updated Event
     * Update stock records if needed
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue.product-updated}")
    public void handleProductUpdated(ProductUpdatedEvent event) {
        log.info("Received ProductUpdatedEvent for product: {} (UUID: {})", event.getProductId(), event.getUuid());

        try {
            inventoryService.handleProductUpdate(event);
            log.info("Successfully handled product update for product: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error handling ProductUpdatedEvent for product: {}", event.getProductId(), e);
        }
    }

    /**
     * Handle Product Deleted Event
     * Soft delete stock records
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue.product-deleted}")
    public void handleProductDeleted(ProductDeletedEvent event) {
        log.info("Received ProductDeletedEvent for product: {}", event.getProductId());

        try {
            inventoryService.handleProductDeletion(event.getProductId());
            log.info("Successfully handled product deletion for product: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error handling ProductDeletedEvent for product: {}", event.getProductId(), e);
        }
    }

    /**
     * Product Deleted Event DTO
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ProductDeletedEvent implements java.io.Serializable {
        private Long productId;
        private String uuid;
    }
}
