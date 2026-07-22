package org.psint.beyosclothing.modules.customers.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Customer Queue Initializer
 * Ensures all customer-related queues and bindings are properly created in RabbitMQ
 * This runs AFTER the application is fully started, guaranteeing all beans are available
 */
@Component
@Slf4j
public class CustomerQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final Queue customerLookupRequestQueue;
    private final Queue customerByUserIdRequestQueue;
    private final Queue customerDetailsRequestQueue;
    private final Queue customerSearchRequestQueue;
    private final TopicExchange customerExchange;

    public CustomerQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("customerLookupRequestQueue") Queue customerLookupRequestQueue,
            @Qualifier("customerByUserIdRequestQueue") Queue customerByUserIdRequestQueue,
            @Qualifier("customerDetailsRequestQueue") Queue customerDetailsRequestQueue,
            @Qualifier("customerSearchRequestQueue") Queue customerSearchRequestQueue,
            @Qualifier("customerExchange") TopicExchange customerExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.customerLookupRequestQueue = customerLookupRequestQueue;
        this.customerByUserIdRequestQueue = customerByUserIdRequestQueue;
        this.customerDetailsRequestQueue = customerDetailsRequestQueue;
        this.customerSearchRequestQueue = customerSearchRequestQueue;
        this.customerExchange = customerExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Customer Module Queues and Bindings ===");

        try {
            // Ensure exchange exists
            rabbitAdmin.declareExchange(customerExchange);
            log.info("✅ Exchange declared: {}", customerExchange.getName());

            // Declare all queues
            rabbitAdmin.declareQueue(customerLookupRequestQueue);
            log.info("✅ Queue declared: {}", customerLookupRequestQueue.getName());

            rabbitAdmin.declareQueue(customerByUserIdRequestQueue);
            log.info("✅ Queue declared: {}", customerByUserIdRequestQueue.getName());

            rabbitAdmin.declareQueue(customerDetailsRequestQueue);
            log.info("✅ Queue declared: {}", customerDetailsRequestQueue.getName());

            rabbitAdmin.declareQueue(customerSearchRequestQueue);
            log.info("✅ Queue declared: {}", customerSearchRequestQueue.getName());

            // Create bindings
            Binding customerLookupBinding = BindingBuilder
                    .bind(customerLookupRequestQueue)
                    .to(customerExchange)
                    .with("customer.lookup.request");
            rabbitAdmin.declareBinding(customerLookupBinding);
            log.info("✅ Binding created: {} -> {} with routing key: customer.lookup.request",
                    customerExchange.getName(), customerLookupRequestQueue.getName());

            Binding customerByUserIdBinding = BindingBuilder
                    .bind(customerByUserIdRequestQueue)
                    .to(customerExchange)
                    .with("customer.by.userid.request");
            rabbitAdmin.declareBinding(customerByUserIdBinding);
            log.info("✅ Binding created: {} -> {} with routing key: customer.by.userid.request",
                    customerExchange.getName(), customerByUserIdRequestQueue.getName());

            Binding customerDetailsBinding = BindingBuilder
                    .bind(customerDetailsRequestQueue)
                    .to(customerExchange)
                    .with("customer.details.request");
            rabbitAdmin.declareBinding(customerDetailsBinding);
            log.info("✅ Binding created: {} -> {} with routing key: customer.details.request",
                    customerExchange.getName(), customerDetailsRequestQueue.getName());

            Binding customerSearchBinding = BindingBuilder
                    .bind(customerSearchRequestQueue)
                    .to(customerExchange)
                    .with("customer.search.request");
            rabbitAdmin.declareBinding(customerSearchBinding);
            log.info("✅ Binding created: {} -> {} with routing key: customer.search.request",
                    customerExchange.getName(), customerSearchRequestQueue.getName());

            log.info("=== Customer Module Initialization Complete ===");

        } catch (Exception e) {
            log.error("❌ Failed to initialize customer module queues and bindings", e);
        }
    }
}

