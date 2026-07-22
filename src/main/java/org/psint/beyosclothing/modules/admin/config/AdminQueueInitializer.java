package org.psint.beyosclothing.modules.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Admin Queue Initializer
 * Ensures all admin-related queues and bindings are properly declared in RabbitMQ
 * on every startup. Runs AFTER the application is fully started via ApplicationReadyEvent.
 *
 * Solves the problem where RabbitMQ broker retains queues across restarts
 * but loses bindings — causing messages to be silently dropped.
 */
@Component
@Slf4j
public class AdminQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange adminExchange;

    @Value("${app.rabbitmq.queue.admin-details-lookup-request:admin.details.lookup.request}")
    private String adminDetailsLookupQueueName;

    public AdminQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("adminExchange") TopicExchange adminExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.adminExchange = adminExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Admin Module Queues and Bindings ===");

        try {
            // Ensure exchange exists
            rabbitAdmin.declareExchange(adminExchange);
            log.info("✅ Exchange declared: {}", adminExchange.getName());

            // Build and declare admin details lookup queue
            Queue adminDetailsQueue = QueueBuilder.durable(adminDetailsLookupQueueName).build();
            rabbitAdmin.declareQueue(adminDetailsQueue);
            log.info("✅ Queue declared: {}", adminDetailsLookupQueueName);

            // Bind queue to admin exchange
            Binding adminDetailsBinding = BindingBuilder
                    .bind(adminDetailsQueue)
                    .to(adminExchange)
                    .with("admin.details.lookup.request");
            rabbitAdmin.declareBinding(adminDetailsBinding);
            log.info("✅ Binding declared: {} → {} [admin.details.lookup.request]",
                    adminExchange.getName(), adminDetailsLookupQueueName);

            log.info("=== Admin Module Queue Initialization Complete ===");

        } catch (Exception e) {
            log.error("❌ Failed to initialize Admin module queues and bindings", e);
        }
    }
}
