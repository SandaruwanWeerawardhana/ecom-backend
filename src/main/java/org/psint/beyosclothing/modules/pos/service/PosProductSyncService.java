

package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.inventory.consumer.ProductEventConsumer.ProductDeletedEvent;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.springframework.amqp.core.Message;

/**
 * Service interface for handling POS product synchronization operations.
 * Provides transactional methods for processing product lifecycle events.
 */
public interface PosProductSyncService {

    /**
     * Processes PRODUCT_CREATED event and synchronizes to MySQL cache and Elasticsearch.
     *
     * @param event   ProductCreatedEvent
     * @param message AMQP message
     */
    void processProductCreated(ProductCreatedEvent event, Message message);

    /**
     * Processes PRODUCT_UPDATED event and synchronizes to MySQL cache and Elasticsearch.
     *
     * @param event   ProductUpdatedEvent
     * @param message AMQP message
     */
    void processProductUpdated(ProductUpdatedEvent event, Message message);

    /**
     * Processes PRODUCT_DELETED event and soft-deletes from MySQL cache and Elasticsearch.
     *
     * @param event   ProductDeletedEvent
     * @param message AMQP message
     */
    void processProductDeleted(ProductDeletedEvent event, Message message);
}


