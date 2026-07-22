package org.psint.beyosclothing.modules.pos.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.event.PosInventoryDeductionEvent;
import org.psint.beyosclothing.modules.pos.dto.event.PosInventoryDeductionEvent.InventoryDeductionItem;
import org.psint.beyosclothing.modules.pos.exception.PosSyncException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * POS Inventory Event Publisher
 * <p>
 * Publishes fire-and-forget inventory deduction events to the inventory module
 * via RabbitMQ. Implements durable messaging, exponential backoff retries,
 * and comprehensive audit logging.
 * <p>
 * Features:
 * - Durable message persistence (survives broker restarts)
 * - Exponential backoff retry (1s, 5s, 30s)
 * - Full audit logging with context
 * - Operational alerts on max retry exceeded
 * - Fire-and-forget pattern (no confirmation wait)
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@Component
@Slf4j
public class PosInventoryEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    @Value("${app.rabbitmq.exchange.pos-inventory:beyos.exchange.pos.inventory}")
    private String posInventoryExchange;

    public PosInventoryEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
    }

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 5000, 30000}; // 1s, 5s, 30s

    // Event type constant
    private static final String EVENT_TYPE_POS_ORDER_PLACED = "POS_ORDER_PLACED";

    // Routing key for inventory deduction
    private static final String ROUTING_KEY_DEDUCTION = "pos.inventory.deduct";

    /**
     * Publishes a stock deduction request to the inventory module.
     * <p>
     * This is a fire-and-forget operation. The message is sent to RabbitMQ
     * and the inventory module will process it asynchronously.
     *
     * @param orderId     POS order ID
     * @param orderUuid   POS order UUID
     * @param items       List of items to deduct (must include productId, variantId, quantity)
     * @throws PosSyncException if publishing fails after all retry attempts
     */
    public void publishStockDeduction(Long orderId, String orderUuid, List<InventoryDeductionItem> items) {
        // Validate input
        if (orderId == null || orderUuid == null || items == null || items.isEmpty()) {
            log.error("[POS_INVENTORY_PUBLISHER] Invalid input - orderId={}, orderUuid={}, itemCount={}",
                    orderId, orderUuid, items != null ? items.size() : 0);
            throw new IllegalArgumentException("orderId, orderUuid, and items are required");
        }

        // Build event payload
        PosInventoryDeductionEvent event = buildEvent(orderId, orderUuid, items);

        // Log publish initiation
        log.info("[POS_INVENTORY_PUBLISHER] Initiating stock deduction publish - orderId={}, orderUuid={}, itemCount={}, timestamp={}",
                orderId, orderUuid, items.size(), event.getTimestamp());

        // Publish with retry logic
        publishWithRetry(event, 0);
    }

    /**
     * Publishes the event with exponential backoff retry logic.
     *
     * @param event        Event to publish
     * @param attemptCount Current attempt count (0-indexed)
     */
    private void publishWithRetry(PosInventoryDeductionEvent event, int attemptCount) {
        try {
            // Attempt to publish
            publishEvent(event);

            // Success - log and return
            log.info("[POS_INVENTORY_PUBLISHER] Successfully published stock deduction - orderId={}, orderUuid={}, attempt={}, itemCount={}",
                    event.getOrderId(), event.getOrderUuid(), attemptCount + 1, event.getItems().size());

        } catch (RuntimeException ex) {
            // Publish failed (AmqpException is a RuntimeException subclass)
            if (attemptCount < MAX_RETRY_ATTEMPTS - 1) {
                // Retry is possible
                long delayMs = RETRY_DELAYS_MS[attemptCount];

                log.warn("[POS_INVENTORY_PUBLISHER] Publish failed, retrying in {}ms - orderId={}, orderUuid={}, attempt={}/{}, error={}",
                        delayMs, event.getOrderId(), event.getOrderUuid(), attemptCount + 1, MAX_RETRY_ATTEMPTS, ex.getMessage());

                // Wait before retry
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("[POS_INVENTORY_PUBLISHER] Retry sleep interrupted - orderId={}, orderUuid={}",
                            event.getOrderId(), event.getOrderUuid(), ie);
                }

                // Recursive retry
                publishWithRetry(event, attemptCount + 1);

            } else {
                // Max retries exceeded - log critical error and trigger alert
                log.error("[POS_INVENTORY_PUBLISHER] CRITICAL: Max retries exceeded for stock deduction - orderId={}, orderUuid={}, itemCount={}, totalAttempts={}",
                        event.getOrderId(), event.getOrderUuid(), event.getItems().size(), MAX_RETRY_ATTEMPTS, ex);

                // Trigger operational alert
                triggerOperationalAlert(event, ex);

                // Throw exception to notify caller
                throw new PosSyncException(
                        "Failed to publish stock deduction after " + MAX_RETRY_ATTEMPTS + " attempts for orderId=" + event.getOrderId(),
                        ex
                );
            }
        }
    }

    /**
     * Publishes the event to RabbitMQ with durable message properties.
     *
     * @param event Event to publish
     * @throws AmqpException if publishing fails
     */
    private void publishEvent(PosInventoryDeductionEvent event) throws AmqpException {
        // Convert event to AMQP message
        Message message = messageConverter.toMessage(event, createDurableMessageProperties(event));

        // Publish to exchange with routing key
        rabbitTemplate.send(posInventoryExchange, ROUTING_KEY_DEDUCTION, message);

        log.debug("[POS_INVENTORY_PUBLISHER] Message sent to exchange={}, routingKey={}, orderId={}",
                posInventoryExchange, ROUTING_KEY_DEDUCTION, event.getOrderId());
    }

    /**
     * Builds the inventory deduction event from input parameters.
     *
     * @param orderId   Order ID
     * @param orderUuid Order UUID
     * @param items     Deduction items
     * @return Constructed event
     */
    private PosInventoryDeductionEvent buildEvent(Long orderId, String orderUuid, List<InventoryDeductionItem> items) {
        return PosInventoryDeductionEvent.builder()
                .eventType(EVENT_TYPE_POS_ORDER_PLACED)
                .orderId(orderId)
                .orderUuid(orderUuid)
                .items(items)
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    /**
     * Creates durable message properties to ensure message persistence.
     * Messages will survive broker restarts.
     *
     * @param event Event being published
     * @return MessageProperties configured for durability
     */
    private MessageProperties createDurableMessageProperties(PosInventoryDeductionEvent event) {
        MessageProperties properties = new MessageProperties();

        // Enable message persistence (survives broker restart)
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE); // Persistent = 2

        // Set content type
        properties.setContentType("application/json");
        properties.setContentEncoding("UTF-8");

        // Set message ID for traceability
        properties.setMessageId(UUID.randomUUID().toString());

        // Set correlation ID (use order UUID)
        properties.setCorrelationId(event.getOrderUuid());

        // Set timestamp
        properties.setTimestamp(java.util.Date.from(event.getTimestamp().toInstant(ZoneOffset.UTC)));

        // Set custom headers for routing and debugging
        properties.setHeader("eventType", event.getEventType());
        properties.setHeader("orderId", event.getOrderId());
        properties.setHeader("orderUuid", event.getOrderUuid());
        properties.setHeader("itemCount", event.getItems().size());

        // Set priority (optional - can be used for prioritization)
        properties.setPriority(5); // Default priority

        return properties;
    }

    /**
     * Triggers an operational alert to notify the operations team
     * when max retry attempts are exceeded.
     *
     * @param event     Event that failed to publish
     * @param exception Exception that caused the failure
     */
    private void triggerOperationalAlert(PosInventoryDeductionEvent event, Exception exception) {
        // For now, log a structured alert message that can be picked up by monitoring tools

        String alertMessage = String.format(
                "ALERT: POS Inventory Deduction Publish Failure | " +
                        "OrderId=%d | OrderUuid=%s | ItemCount=%d | " +
                        "Timestamp=%s | Error=%s",
                event.getOrderId(),
                event.getOrderUuid(),
                event.getItems().size(),
                event.getTimestamp().format(DateTimeFormatter.ISO_DATE_TIME),
                exception.getMessage()
        );

        log.error("[OPERATIONAL_ALERT] {}", alertMessage, exception);

        // Additional alerting integrations can be added here:
        // - Send to metrics/monitoring system (Prometheus, Datadog, etc.)
        // - Send to communication channel (Slack, Teams, etc.)
        // - Store in database for operational dashboard
    }

    /**
     * Overloaded method for backward compatibility - accepts raw item list.
     *
     * @param orderId Order ID
     * @param items   List of items (must be convertible to InventoryDeductionItem)
     */
    public void publishStockDeduction(Long orderId, List<InventoryDeductionItem> items) {
        // Generate UUID if not provided
        String orderUuid = "POS-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8);
        publishStockDeduction(orderId, orderUuid, items);
    }
}
