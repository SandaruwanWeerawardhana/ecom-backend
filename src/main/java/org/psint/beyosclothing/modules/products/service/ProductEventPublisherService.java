package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductPriceChangedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.products.events.VariantCreatedEvent;

/**
 * Product Event Publisher Service
 * Publishes product-related events to RabbitMQ for inventory module consumption
 */
public interface ProductEventPublisherService {

    /**
     * Publish product created event
     */
    void publishProductCreated(ProductCreatedEvent event);

    /**
     * Publish product updated event
     */
    void publishProductUpdated(ProductUpdatedEvent event);

    /**
     * Publish product deleted event
     */
    void publishProductDeleted(Long productId, String uuid);

    /**
     * Publish product price changed event
     */
    void publishProductPriceChanged(ProductPriceChangedEvent event);

    /**
     * Publish variant created event
     */
    void publishVariantCreated(VariantCreatedEvent event);
}
