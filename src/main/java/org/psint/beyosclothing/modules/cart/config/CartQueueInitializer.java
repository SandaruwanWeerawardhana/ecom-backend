package org.psint.beyosclothing.modules.cart.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cart Module Queue Initializer
 * Ensures all cart-related queues and bindings are properly declared
 * in RabbitMQ on every startup.
 */
@Component
@Slf4j
public class CartQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange cartExchange;
    private final TopicExchange customerExchange;
    private final TopicExchange deliveryExchange;

    @Value("${app.rabbitmq.queue.cart-checkout-request:${app.rabbitmq.queue.cart-items-checkout-request:cart.checkout.request.queue}}")
    private String cartCheckoutRequestQueueName;

    @Value("${app.rabbitmq.queue.cart-items-clear-request:cart.items.clear.request.queue}")
    private String cartItemsClearRequestQueueName;

    @Value("${app.rabbitmq.queue.customer-address-lookup-request:customer.address.lookup.request.queue}")
    private String customerAddressLookupRequestQueueName;

    @Value("${app.rabbitmq.queue.delivery-shipping-calculate-request:delivery.shipping.calculate.request.queue}")
    private String deliveryShippingCalculateRequestQueueName;

    public CartQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("cartExchange") TopicExchange cartExchange,
            @Qualifier("customerExchange") TopicExchange customerExchange,
            @Qualifier("deliveryExchange") TopicExchange deliveryExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.cartExchange = cartExchange;
        this.customerExchange = customerExchange;
        this.deliveryExchange = deliveryExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Cart Module Queues and Bindings ===");

        try {
            // ========================================
            // Cart Checkout Request Queue + Binding
            // ========================================
            Queue cartCheckoutRequestQueue = new Queue(cartCheckoutRequestQueueName, true);
            rabbitAdmin.declareQueue(cartCheckoutRequestQueue);
            log.info("✅ Queue declared: {}", cartCheckoutRequestQueueName);

            Binding cartCheckoutBinding = BindingBuilder
                    .bind(cartCheckoutRequestQueue)
                    .to(cartExchange)
                    .with("cart.items.checkout.request");
            rabbitAdmin.declareBinding(cartCheckoutBinding);
            log.info("✅ Binding created: {} -> {} with routing key: cart.items.checkout.request",
                    cartExchange.getName(), cartCheckoutRequestQueueName);

            // ========================================
            // Cart Items Clear Request Queue + Binding
            // ========================================
            Queue cartItemsClearRequestQueue = new Queue(cartItemsClearRequestQueueName, true);
            rabbitAdmin.declareQueue(cartItemsClearRequestQueue);
            log.info("Queue declared: {}", cartItemsClearRequestQueueName);

            Binding cartItemsClearBinding = BindingBuilder
                    .bind(cartItemsClearRequestQueue)
                    .to(cartExchange)
                    .with("cart.items.clear.request");
            rabbitAdmin.declareBinding(cartItemsClearBinding);
            log.info("Binding created: {} -> {} with routing key: cart.items.clear.request",
                    cartExchange.getName(), cartItemsClearRequestQueueName);

            // ========================================
            // Customer Address Lookup Request Queue + Binding
            // ========================================
            Queue customerAddressLookupQueue = new Queue(customerAddressLookupRequestQueueName, true);
            rabbitAdmin.declareQueue(customerAddressLookupQueue);
            log.info("✅ Queue declared: {}", customerAddressLookupRequestQueueName);

            Binding customerAddressBinding = BindingBuilder
                    .bind(customerAddressLookupQueue)
                    .to(customerExchange)
                    .with("customer.address.lookup.request");
            rabbitAdmin.declareBinding(customerAddressBinding);
            log.info("✅ Binding created: {} -> {} with routing key: customer.address.lookup.request",
                    customerExchange.getName(), customerAddressLookupRequestQueueName);

            // ========================================
            // Delivery Shipping Calculate Request Queue + Binding
            // ========================================
            Queue deliveryShippingCalculateQueue = new Queue(deliveryShippingCalculateRequestQueueName, true);
            rabbitAdmin.declareQueue(deliveryShippingCalculateQueue);
            log.info("✅ Queue declared: {}", deliveryShippingCalculateRequestQueueName);

            Binding deliveryShippingBinding = BindingBuilder
                    .bind(deliveryShippingCalculateQueue)
                    .to(deliveryExchange)
                    .with("delivery.shipping.calculate.request");
            rabbitAdmin.declareBinding(deliveryShippingBinding);
            log.info("✅ Binding created: {} -> {} with routing key: delivery.shipping.calculate.request",
                    deliveryExchange.getName(), deliveryShippingCalculateRequestQueueName);

            log.info("=== Cart Module Queue Initialization Complete ===");

        } catch (Exception e) {
            log.error("❌ Failed to initialize Cart module queues and bindings", e);
        }
    }
}
