package org.psint.beyosclothing.modules.pos.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * POS RabbitMQ Retry Handler
 * Implements exponential backoff retry mechanism for POS message processing
 * Retry strategy: 1s → 5s → 30s → DLQ
 */
@Component
@Slf4j
public class PosRetryHandler {

    private final RabbitTemplate rabbitTemplate;
    private static final String POS_DLX_EXCHANGE = "pos.dlx.exchange";
    private static final int MAX_RETRIES = 3;

    public PosRetryHandler(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Handle message retry with exponential backoff
     * @param message The failed message
     * @param queueBaseName Base queue name (e.g., "pos.product.sync")
     * @param exception The exception that caused the failure
     */
    public void handleRetry(Message message, String queueBaseName, Exception exception) {
        int retryCount = getRetryCount(message);

        if (retryCount >= MAX_RETRIES) {
            log.error("Max retries ({}) exceeded for message, sending to DLQ: {}",
                    MAX_RETRIES, queueBaseName, exception);
            sendToDlq(message, queueBaseName);
            return;
        }

        int nextRetry = retryCount + 1;
        String retryRoutingKey = getRetryRoutingKey(queueBaseName, nextRetry);

        log.warn("Retry attempt {}/{} for message in queue: {} - Error: {}",
                nextRetry, MAX_RETRIES, queueBaseName, exception.getMessage());

        // Update retry count in message headers
        message.getMessageProperties().setHeader("x-retry-count", nextRetry);

        // Send to appropriate retry queue
        rabbitTemplate.send(POS_DLX_EXCHANGE, retryRoutingKey, message);
    }

    /**
     * Get current retry count from message headers
     */
    private Integer getRetryCount(Message message) {
        Object retryCount = message.getMessageProperties().getHeader("x-retry-count");
        return retryCount != null ? (Integer) retryCount : 0;
    }

    /**
     * Generate retry routing key based on retry attempt
     * Attempt 1: 1s delay
     * Attempt 2: 5s delay
     * Attempt 3: 30s delay
     */
    private String getRetryRoutingKey(String queueBaseName, int retryAttempt) {
        return queueBaseName + ".retry." + retryAttempt;
    }

    /**
     * Send message to dead letter queue after max retries exceeded
     */
    private void sendToDlq(Message message, String queueBaseName) {
        String dlqRoutingKey = queueBaseName + ".dlq";
        message.getMessageProperties().setHeader("x-retry-count", MAX_RETRIES);
        message.getMessageProperties().setHeader("x-failed-at", System.currentTimeMillis());
        rabbitTemplate.send(POS_DLX_EXCHANGE, dlqRoutingKey, message);
        log.error("Message sent to DLQ: {}", dlqRoutingKey);
    }

    /**
     * Check if message should be retried based on exception type
     * Some exceptions should not be retried (e.g., validation errors)
     */
    public boolean shouldRetry(Exception exception) {
        // Don't retry for validation/business logic errors
        return !(exception instanceof IllegalArgumentException)
                && !(exception instanceof IllegalStateException);
        // Retry for transient errors (network, DB connection, etc.)
    }
}
