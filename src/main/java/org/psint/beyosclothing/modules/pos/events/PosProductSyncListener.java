package org.psint.beyosclothing.modules.pos.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.consumer.ProductEventConsumer.ProductDeletedEvent;
import org.psint.beyosclothing.modules.pos.exception.PosSyncException;
import org.psint.beyosclothing.modules.pos.service.PosProductSyncService;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;

/**
 * POS Product Synchronization Event Listener
 * <p>
 * Consumes product lifecycle events (PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED)
 * from the product module and maintains consistency between:
 * - MySQL POS product cache (pos_product_cache table)
 * - Elasticsearch pos_products index
 * <p>
 * Features:
 * - Idempotent processing (safe to replay events)
 * - Dual-write consistency (MySQL first, then Elasticsearch)
 * - Graceful error handling (logs errors without crashing)
 * - Soft-delete strategy for deleted products
 * - Race condition handling with optimistic locking
 * - Comprehensive audit logging
 * - Resilient to Elasticsearch failures
 *
 * @author Beyos Development Team
 * @version 2.0
 * @since 2026-02-03
 */
@RequiredArgsConstructor
@Slf4j
public class PosProductSyncListener {

    private final PosProductSyncService syncService;
    private final ObjectMapper objectMapper;

    // Constants for event types and JSON field names (centralized)
    private static final String EVENT_PRODUCT_CREATED = "PRODUCT_CREATED";
    private static final String EVENT_PRODUCT_UPDATED = "PRODUCT_UPDATED";
    private static final String EVENT_PRODUCT_DELETED = "PRODUCT_DELETED";

    private static final String JSON_PRODUCT_ID = "\"productId\"";
    private static final String JSON_CREATED_AT = "\"createdAt\"";
    private static final String JSON_UPDATED_AT = "\"updatedAt\"";
    private static final String JSON_UUID = "\"uuid\"";

    // ========================================
    // Unified Event Listener (Routes Based on Event Type)
    // ========================================

    /**
     * Unified listener that handles all product lifecycle events.
     * Routes to appropriate handler based on event type header or payload inspection.
     *
     * @param message Raw AMQP message containing the event
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.pos-product-sync:pos.product.sync.queue}",
            ackMode = "AUTO",
            concurrency = "3-10"
    )
    public void onProductEvent(Message message) {
        String eventType = extractEventType(message);
        String messageId = message.getMessageProperties().getMessageId();

        log.debug("[POS_SYNC] Received event - type={}, messageId={}", eventType, messageId);

        try {
            switch (eventType) {
                case EVENT_PRODUCT_CREATED:
                    ProductCreatedEvent createdEvent = deserializeEvent(message, ProductCreatedEvent.class);
                    syncService.processProductCreated(createdEvent, message);
                    break;

                case EVENT_PRODUCT_UPDATED:
                    ProductUpdatedEvent updatedEvent = deserializeEvent(message, ProductUpdatedEvent.class);
                    syncService.processProductUpdated(updatedEvent, message);
                    break;

                case EVENT_PRODUCT_DELETED:
                    ProductDeletedEvent deletedEvent = deserializeEvent(message, ProductDeletedEvent.class);
                    syncService.processProductDeleted(deletedEvent, message);
                    break;

                default:
                    log.warn("[POS_SYNC] Unknown event type: {} - messageId={}", eventType, messageId);
                    // Ack the message to prevent reprocessing
            }

        } catch (MessageConversionException mce) {
            log.error("[POS_SYNC] Failed to deserialize event - type={}, messageId={}, error={}",
                    eventType, messageId, mce.getMessage());
            // Don't rethrow - ack message to prevent infinite retry loop

        } catch (Exception ex) {
            log.error("[POS_SYNC] Unexpected error processing event - type={}, messageId={}",
                    eventType, messageId, ex);
            // Rethrow as project-specific PosSyncException to use custom exception handling
            throw new PosSyncException("Failed to process product event, type=" + eventType + ", messageId=" + messageId, ex);
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Extracts the event type from the message headers or payload.
     *
     * @param message AMQP message
     * @return Event type string (PRODUCT_CREATED, PRODUCT_UPDATED, or PRODUCT_DELETED)
     */
    private String extractEventType(Message message) {
        // First try to get from message header
        Object eventTypeHeader = message.getMessageProperties().getHeader("eventType");
        if (eventTypeHeader != null) {
            return eventTypeHeader.toString();
        }

        // Try to get from message properties type field
        String messageType = message.getMessageProperties().getType();
        if (messageType != null && !messageType.isEmpty()) {
            return messageType;
        }

        // Last resort: try to parse from message body JSON
        try {
            String body = new String(message.getBody());
            if (body.contains(JSON_PRODUCT_ID) && body.contains(JSON_CREATED_AT)) {
                return EVENT_PRODUCT_CREATED;
            } else if (body.contains(JSON_PRODUCT_ID) && body.contains(JSON_UPDATED_AT)) {
                return EVENT_PRODUCT_UPDATED;
            } else if (body.contains(JSON_PRODUCT_ID) && body.contains(JSON_UUID) && body.length() < 100) {
                return EVENT_PRODUCT_DELETED;
            }
        } catch (Exception ex) {
            log.warn("[POS_SYNC] Failed to extract event type from message body", ex);
        }

        return "UNKNOWN";
    }

    /**
     * Deserializes a message into the specified event type.
     *
     * @param message    AMQP message
     * @param eventClass Class of the event to deserialize to
     * @param <T>        Event type
     * @return Deserialized event object
     * @throws MessageConversionException if deserialization fails
     */
    private <T> T deserializeEvent(Message message, Class<T> eventClass) throws MessageConversionException {
        try {
            String body = new String(message.getBody());
            return objectMapper.readValue(body, eventClass);
        } catch (Exception ex) {
            throw new MessageConversionException("Failed to deserialize event: " + ex.getMessage(), ex);
        }
    }
}
