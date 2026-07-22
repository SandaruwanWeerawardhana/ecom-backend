package org.psint.beyosclothing.modules.orders.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue Initializer for Order Courier Placement
 * Declares queues and bindings for placing orders with courier service
 * Uses exchanges from RabbitMQConfig to avoid conflicts
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class OrderCourierQueueInitializer {

    private final TopicExchange orderExchange;
    private final TopicExchange deliveryExchange;
    private final TopicExchange paymentExchange;

    // ========== Order Exchange & Queues ==========

    @Bean
    public Queue orderPlacedWithCourierQueue() {
        return new Queue("order.placed.with.courier", true);
    }

    @Bean
    public Binding orderPlacedWithCourierBinding(Queue orderPlacedWithCourierQueue) {
        Binding binding = BindingBuilder.bind(orderPlacedWithCourierQueue)
                .to(orderExchange)
                .with("order.placed.with.courier");

        log.info("✅ Binding created: {} -> order.placed.with.courier with routing key: order.placed.with.courier",
                orderExchange.getName());
        return binding;
    }

    // ========== Delivery Exchange & Queues ==========

    @Bean
    public Queue activeCourierLookupRequestQueue() {
        return new Queue("active.courier.lookup.request", true);
    }

    @Bean
    public Binding activeCourierLookupRequestBinding(Queue activeCourierLookupRequestQueue) {
        Binding binding = BindingBuilder.bind(activeCourierLookupRequestQueue)
                .to(deliveryExchange)
                .with("active.courier.lookup.request");

        log.info("✅ Binding created: {} -> active.courier.lookup.request with routing key: active.courier.lookup.request",
                deliveryExchange.getName());
        return binding;
    }

    @Bean
    public Queue shipmentPlacedWithCourierQueue() {
        return new Queue("shipment.placed.with.courier", true);
    }

    @Bean
    public Binding shipmentPlacedWithCourierBinding(Queue shipmentPlacedWithCourierQueue) {
        Binding binding = BindingBuilder.bind(shipmentPlacedWithCourierQueue)
                .to(deliveryExchange)
                .with("shipment.placed.with.courier");

        log.info("✅ Binding created: {} -> shipment.placed.with.courier with routing key: shipment.placed.with.courier",
                deliveryExchange.getName());
        return binding;
    }

    // ========== Payment Exchange & Queues ==========

    @Bean
    public Queue orderCourierPaymentTransactionCreateQueue() {
        return new Queue("order.courier.payment.transaction.create", true);
    }

    @Bean
    public Binding orderCourierPaymentTransactionCreateBinding(Queue orderCourierPaymentTransactionCreateQueue) {
        Binding binding = BindingBuilder.bind(orderCourierPaymentTransactionCreateQueue)
                .to(paymentExchange)
                .with("order.courier.payment.transaction.create");

        log.info("✅ Binding created: {} -> order.courier.payment.transaction.create with routing key: order.courier.payment.transaction.create",
                paymentExchange.getName());
        return binding;
    }
}
