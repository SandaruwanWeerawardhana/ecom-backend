package org.psint.beyosclothing.modules.pos.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class PosProductSyncConsumer {

    private final PosRetryHandler retryHandler;

    /**
     * Main consumer method with manual acknowledgment
     *
     * @param message Raw RabbitMQ message
     * @param channel RabbitMQ channel for manual ack/nack
     * @param payload Deserialized message payload
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.pos-product-sync}",
            ackMode = "MANUAL",
            concurrency = "3-10"
    )
    public void consumeProductEvent(Message message, Channel channel, Map<String, Object> payload) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String eventType = (String) payload.get("eventType");

        try {
            log.info("Received product event: {} - Payload: {}", eventType, payload);

            // Idempotency check - prevent duplicate processing
            String messageId = message.getMessageProperties().getMessageId();
            if (isAlreadyProcessed(messageId)) {
                log.info("Message already processed, skipping: {}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // Process based on event type
            switch (eventType) {
                case "PRODUCT_CREATED":
                    handleProductCreated(payload);
                    break;
                case "PRODUCT_UPDATED":
                    handleProductUpdated(payload);
                    break;
                case "PRODUCT_DELETED":
                    handleProductDeleted(payload);
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
            }

            // Mark as processed
            markAsProcessed(messageId);

            // Manual acknowledgment - message processed successfully
            channel.basicAck(deliveryTag, false);
            log.info("Product event processed successfully: {}", eventType);

        } catch (Exception e) {
            log.error("Error processing product event: {} - Error: {}", eventType, e.getMessage(), e);

            try {
                // Check if should retry
                if (retryHandler.shouldRetry(e)) {
                    // Negative acknowledgment - don't requeue, let retry handler manage it
                    channel.basicNack(deliveryTag, false, false);
                    // Send to retry queue with exponential backoff
                    retryHandler.handleRetry(message, "pos.product.sync", e);
                } else {
                    // Business logic error - ack and log
                    channel.basicAck(deliveryTag, false);
                    log.error("Non-retryable error, message acknowledged: {}", e.getMessage());
                }
            } catch (IOException ioException) {
                log.error("Error during message acknowledgment", ioException);
            }
        }
    }

    /**
     * Handle product created event
     */
    private void handleProductCreated(Map<String, Object> payload) {
        Long productId = ((Number) payload.get("productId")).longValue();
        String productTitle = (String) payload.get("title");

        log.info("Creating POS product cache entry: {} - {}", productId, productTitle);

        // Simulate processing
        if (Math.random() < 0.1) { // 10% failure rate for demo
            throw new RuntimeException("Simulated cache creation failure");
        }
    }

    /**
     * Handle product updated event
     */
    private void handleProductUpdated(Map<String, Object> payload) {
        Long productId = ((Number) payload.get("productId")).longValue();

        log.info("Updating POS product cache entry: {}", productId);

    }

    /**
     * Handle product deleted event
     */
    private void handleProductDeleted(Map<String, Object> payload) {
        Long productId = ((Number) payload.get("productId")).longValue();

        log.info("Deleting POS product cache entry: {}", productId);

    }

    /**
     * Check if message was already processed (idempotency)
     */
    private boolean isAlreadyProcessed(String messageId) {
        // Check in Redis/DB if this message was already processed
        return false;
    }

    /**
     * Mark message as processed (idempotency)
     */
    private void markAsProcessed(String messageId) {
        // Store in Redis/DB with TTL (e.g., 24 hours)
        // This prevents duplicate processing if message is redelivered
    }
}
