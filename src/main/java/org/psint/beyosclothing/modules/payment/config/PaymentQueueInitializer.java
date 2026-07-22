package org.psint.beyosclothing.modules.payment.config;

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
 * Payment Module Queue Initializer
 * Ensures all payment-related queues and bindings are properly declared
 * in RabbitMQ on every startup
 */
@Component
@Slf4j
public class PaymentQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange paymentExchange;

    @Value("${app.rabbitmq.queue.payment-transaction-delivery-confirmed:payment.transaction.delivery.confirmed}")
    private String paymentTransactionDeliveryConfirmedQueueName;

    @Value("${app.rabbitmq.queue.payment-request-create-request:payment.request.create.request.queue}")
    private String paymentRequestCreateRequestQueueName;

    @Value("${app.rabbitmq.queue.payment-status-verify-request:payment.status.verify.request.queue}")
    private String paymentStatusVerifyRequestQueueName;

    public PaymentQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("paymentExchange") TopicExchange paymentExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.paymentExchange = paymentExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Payment Module Queues and Bindings ===");

        try {
            // Ensure exchange exists
            rabbitAdmin.declareExchange(paymentExchange);
            log.info("✅ Exchange declared: {}", paymentExchange.getName());

            // Payment transaction delivery confirmed queue
            Queue paymentTransactionDeliveryConfirmedQueue = QueueBuilder.durable(paymentTransactionDeliveryConfirmedQueueName).build();
            rabbitAdmin.declareQueue(paymentTransactionDeliveryConfirmedQueue);
            Binding paymentTransactionDeliveryConfirmedBinding = BindingBuilder
                    .bind(paymentTransactionDeliveryConfirmedQueue)
                    .to(paymentExchange)
                    .with("payment.transaction.delivery.confirmed");
            rabbitAdmin.declareBinding(paymentTransactionDeliveryConfirmedBinding);
            log.info("✅ Binding declared: {} → payment.transaction.delivery.confirmed", paymentExchange.getName());

            // Payment request creation queue (RPC from Order/Customer modules to create a payment request
            // and, for online methods, produce the gateway checkout URL). Without this binding the request
            // is published to the exchange but routed nowhere, so the caller times out.
            Queue paymentRequestCreateRequestQueue = QueueBuilder.durable(paymentRequestCreateRequestQueueName).build();
            rabbitAdmin.declareQueue(paymentRequestCreateRequestQueue);
            Binding paymentRequestCreateRequestBinding = BindingBuilder
                    .bind(paymentRequestCreateRequestQueue)
                    .to(paymentExchange)
                    .with("payment.request.create.request");
            rabbitAdmin.declareBinding(paymentRequestCreateRequestBinding);
            log.info("✅ Binding declared: {} → payment.request.create.request", paymentExchange.getName());

            // Payment status verification queue (RPC from Customer module to re-verify an order's
            // payment against the gateway status API when the asynchronous callback has not arrived).
            Queue paymentStatusVerifyRequestQueue = QueueBuilder.durable(paymentStatusVerifyRequestQueueName).build();
            rabbitAdmin.declareQueue(paymentStatusVerifyRequestQueue);
            Binding paymentStatusVerifyRequestBinding = BindingBuilder
                    .bind(paymentStatusVerifyRequestQueue)
                    .to(paymentExchange)
                    .with("payment.status.verify.request");
            rabbitAdmin.declareBinding(paymentStatusVerifyRequestBinding);
            log.info("✅ Binding declared: {} → payment.status.verify.request", paymentExchange.getName());

            log.info("=== Payment Module Queue Initialization Complete ===");
        } catch (Exception e) {
            log.error("❌ Failed to initialize Payment module queues and bindings", e);
        }
    }
}

