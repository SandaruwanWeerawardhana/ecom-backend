package org.psint.beyosclothing.modules.pos.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.event.PosOrderCompletedEvent;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * POS Order Event Publisher for Reporting and Analytics
 * <p>
 * Publishes order completion events to a dedicated analytics exchange
 * using a fire-and-forget pattern that never blocks order placement.
 * <p>
 * Architecture:
 * - Publishes to dedicated analytics exchange (separate from operational queues)
 * - Fire-and-forget (asynchronous, non-blocking)
 * - Resilient to broker failures (logs errors but never throws exceptions)
 * - Supports multiple consumers via topic exchange routing
 * - Extensible payload design for backward/forward compatibility
 * <p>
 * Downstream Consumers:
 * - Real-time sales dashboards (routing key: pos.order.completed.dashboard)
 * - Sales reporting and analytics (routing key: pos.order.completed.analytics)
 * - Customer purchase history (routing key: pos.order.completed.customer)
 * - Inventory analytics (routing key: pos.order.completed.inventory)
 * <p>
 * Features:
 * - Non-blocking asynchronous publishing
 * - Automatic error handling (no exceptions thrown)
 * - Comprehensive audit logging
 * - Message durability for reliability
 * - Topic-based routing for flexible subscriptions
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@Component
@Slf4j
public class PosOrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    @Value("${app.rabbitmq.exchange.pos-analytics:beyos.exchange.pos.analytics}")
    private String posAnalyticsExchange;

    // Event type constant
    private static final String EVENT_TYPE_ORDER_COMPLETED = "POS_ORDER_COMPLETED";

    // Routing key for analytics events (supports pattern-based subscriptions)
    private static final String ROUTING_KEY_ORDER_COMPLETED = "pos.order.completed";

    public PosOrderEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
    }

    /**
     * Publishes a POS order completed event for analytics and reporting.
     * <p>
     * This method is fire-and-forget and NEVER blocks or throws exceptions.
     * It executes asynchronously to ensure order placement is not delayed.
     *
     * @param orderUuid      Order UUID
     * @param terminalId     Terminal ID
     * @param cashierId      Cashier ID
     * @param customerId     Customer ID (null for walk-in)
     * @param total          Total amount
     * @param paymentMethod  Payment method (e.g., "CARD", "CASH")
     * @param itemCount      Number of items
     */
    @Async("posEventExecutor") // Uses dedicated thread pool to avoid blocking
    public void publishOrderCompleted(
            String orderUuid,
            Long terminalId,
            Long cashierId,
            Long customerId,
            BigDecimal total,
            String paymentMethod,
            Integer itemCount
    ) {
        try {
            // Build event payload
            PosOrderCompletedEvent event = buildEvent(
                    orderUuid, terminalId, cashierId, customerId,
                    total, paymentMethod, itemCount
            );

            // Log event publication initiation
            log.debug("[POS_ORDER_EVENT_PUBLISHER] Publishing order completed event - orderUuid={}, terminalId={}, cashierId={}, customerId={}, total={}, paymentMethod={}, itemCount={}",
                    orderUuid, terminalId, cashierId, customerId, total, paymentMethod, itemCount);

            // Publish event asynchronously
            publishEventAsync(event);

        } catch (Exception ex) {
            // Catch all exceptions to ensure we NEVER fail order placement
            log.error("[POS_ORDER_EVENT_PUBLISHER] Failed to publish order completed event (non-blocking) - orderUuid={}, error={}",
                    orderUuid, ex.getMessage(), ex);
            // Do NOT rethrow - this is fire-and-forget
        }
    }

    /**
     * Overloaded method with additional optional fields for extended analytics.
     *
     * @param orderUuid        Order UUID
     * @param terminalId       Terminal ID
     * @param cashierId        Cashier ID
     * @param customerId       Customer ID (null for walk-in)
     * @param total            Total amount
     * @param paymentMethod    Payment method
     * @param itemCount        Number of items
     * @param storeId          Store ID (optional)
     * @param discountAmount   Discount amount (optional)
     * @param taxAmount        Tax amount (optional)
     * @param processingTimeMs Processing time in ms (optional)
     */
    @Async("posEventExecutor")
    public void publishOrderCompleted(
            String orderUuid,
            Long terminalId,
            Long cashierId,
            Long customerId,
            BigDecimal total,
            String paymentMethod,
            Integer itemCount,
            Long storeId,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            Long processingTimeMs
    ) {
        try {
            // Build event payload with optional fields
            PosOrderCompletedEvent event = buildEventWithExtensions(
                    orderUuid, terminalId, cashierId, customerId,
                    total, paymentMethod, itemCount,
                    storeId, discountAmount, taxAmount, processingTimeMs
            );

            log.debug("[POS_ORDER_EVENT_PUBLISHER] Publishing extended order completed event - orderUuid={}, storeId={}, processingTimeMs={}",
                    orderUuid, storeId, processingTimeMs);

            publishEventAsync(event);

        } catch (Exception ex) {
            log.error("[POS_ORDER_EVENT_PUBLISHER] Failed to publish extended order completed event - orderUuid={}, error={}",
                    orderUuid, ex.getMessage(), ex);
        }
    }

    /**
     * Publishes the event asynchronously to RabbitMQ with error handling.
     *
     * @param event Event to publish
     */
    private void publishEventAsync(PosOrderCompletedEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                // Convert event to AMQP message with durable properties
                Message message = messageConverter.toMessage(event, createMessageProperties(event));

                // Publish to analytics exchange
                rabbitTemplate.send(posAnalyticsExchange, ROUTING_KEY_ORDER_COMPLETED, message);

                log.info("[POS_ORDER_EVENT_PUBLISHER] Successfully published order completed event - orderUuid={}, terminalId={}, total={}, timestamp={}",
                        event.getOrderUuid(), event.getTerminalId(), event.getTotal(), event.getTimestamp());

            } catch (AmqpException amqpEx) {
                // RabbitMQ-specific errors
                log.error("[POS_ORDER_EVENT_PUBLISHER] AMQP error publishing event (broker issue) - orderUuid={}, error={}",
                        event.getOrderUuid(), amqpEx.getMessage());
                // Consider triggering circuit breaker or fallback here

            } catch (RuntimeException ex) {
                // Other runtime errors
                log.error("[POS_ORDER_EVENT_PUBLISHER] Runtime error publishing event - orderUuid={}, error={}",
                        event.getOrderUuid(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * Builds the basic order completed event.
     */
    private PosOrderCompletedEvent buildEvent(
            String orderUuid,
            Long terminalId,
            Long cashierId,
            Long customerId,
            BigDecimal total,
            String paymentMethod,
            Integer itemCount
    ) {
        return PosOrderCompletedEvent.builder()
                .eventType(EVENT_TYPE_ORDER_COMPLETED)
                .orderUuid(orderUuid)
                .terminalId(terminalId)
                .cashierId(cashierId)
                .customerId(customerId)
                .total(total)
                .paymentMethod(paymentMethod)
                .itemCount(itemCount)
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .channel("POS") // Default channel
                .build();
    }

    /**
     * Builds the order completed event with extended fields.
     */
    private PosOrderCompletedEvent buildEventWithExtensions(
            String orderUuid,
            Long terminalId,
            Long cashierId,
            Long customerId,
            BigDecimal total,
            String paymentMethod,
            Integer itemCount,
            Long storeId,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            Long processingTimeMs
    ) {
        return PosOrderCompletedEvent.builder()
                .eventType(EVENT_TYPE_ORDER_COMPLETED)
                .orderUuid(orderUuid)
                .terminalId(terminalId)
                .cashierId(cashierId)
                .customerId(customerId)
                .total(total)
                .paymentMethod(paymentMethod)
                .itemCount(itemCount)
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .channel("POS")
                // Extended fields
                .storeId(storeId)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * Creates message properties for durable, traceable messages.
     *
     * @param event Event being published
     * @return MessageProperties configured for analytics
     */
    private MessageProperties createMessageProperties(PosOrderCompletedEvent event) {
        MessageProperties properties = new MessageProperties();

        // Enable message persistence (optional for analytics - can be changed to non-persistent for performance)
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

        // Set custom headers for flexible routing and filtering
        properties.setHeader("eventType", event.getEventType());
        properties.setHeader("orderUuid", event.getOrderUuid());
        properties.setHeader("terminalId", event.getTerminalId());
        properties.setHeader("cashierId", event.getCashierId());
        properties.setHeader("paymentMethod", event.getPaymentMethod());
        properties.setHeader("channel", event.getChannel());

        // Optional headers (only if present)
        if (event.getCustomerId() != null) {
            properties.setHeader("customerId", event.getCustomerId());
        }
        if (event.getStoreId() != null) {
            properties.setHeader("storeId", event.getStoreId());
        }

        // Set priority (lower for analytics to not compete with operational messages)
        properties.setPriority(3); // Lower priority than operational events

        return properties;
    }

    /**
     * Checks if the publisher is healthy and can publish events.
     * Useful for health checks and monitoring.
     *
     * @return true if RabbitMQ connection is active
     */
    public boolean isHealthy() {
        try (var connection = rabbitTemplate.getConnectionFactory().createConnection()) {
            return connection.isOpen();
        } catch (Exception ex) {
            log.warn("[POS_ORDER_EVENT_PUBLISHER] Health check failed - error={}", ex.getMessage());
            return false;
        }
    }
}
