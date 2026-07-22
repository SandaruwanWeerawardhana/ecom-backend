package org.psint.beyosclothing.core.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Reseller Module
 * Configures exchanges, queues, and bindings for reseller events
 */
@Configuration
public class ResellerRabbitMQConfig {

    @Value("${app.rabbitmq.exchange.reseller}")
    private String resellerExchange;

    @Value("${app.rabbitmq.queue.reseller-registered}")
    private String resellerRegisteredQueue;

    @Value("${app.rabbitmq.queue.reseller-approved}")
    private String resellerApprovedQueue;

    @Value("${app.rabbitmq.queue.reseller-rejected}")
    private String resellerRejectedQueue;

    @Value("${app.rabbitmq.queue.reseller-suspended}")
    private String resellerSuspendedQueue;

    @Value("${app.rabbitmq.queue.reseller-order-placed}")
    private String resellerOrderPlacedQueue;

    @Value("${app.rabbitmq.queue.reseller-wallet-credited}")
    private String resellerWalletCreditedQueue;

    @Value("${app.rabbitmq.queue.reseller-withdrawal-requested}")
    private String resellerWithdrawalRequestedQueue;

    @Value("${app.rabbitmq.queue.reseller-withdrawal-processed}")
    private String resellerWithdrawalProcessedQueue;

    @Value("${app.rabbitmq.queue.reseller-name-lookup-request:reseller.name.lookup.request}")
    private String resellerNameLookupRequestQueue;

    @Value("${app.rabbitmq.routing-key.reseller-registered}")
    private String resellerRegisteredRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-approved}")
    private String resellerApprovedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-rejected}")
    private String resellerRejectedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-suspended}")
    private String resellerSuspendedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-order-placed}")
    private String resellerOrderPlacedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-wallet-credited}")
    private String resellerWalletCreditedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-withdrawal-requested}")
    private String resellerWithdrawalRequestedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-withdrawal-processed}")
    private String resellerWithdrawalProcessedRoutingKey;

    /**
     * Dead Letter Exchange
     */
    @Bean
    public Queue resellerNameLookupRequestQueue() {
        return QueueBuilder.durable(resellerNameLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerNameLookupBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerNameLookupRequestQueue())
                .to(resellerExchange)
                .with("reseller.name.lookup.request");
    }

    // Dead Letter Exchange
    @Bean
    public DirectExchange resellerDeadLetterExchange() {
        return new DirectExchange(resellerExchange + ".dlx");
    }

    // Queues
    @Bean
    public Queue resellerRegisteredQueue() {
        return QueueBuilder.durable(resellerRegisteredQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerApprovedQueue() {
        return QueueBuilder.durable(resellerApprovedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerRejectedQueue() {
        return QueueBuilder.durable(resellerRejectedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerSuspendedQueue() {
        return QueueBuilder.durable(resellerSuspendedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerOrderPlacedQueue() {
        return QueueBuilder.durable(resellerOrderPlacedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerWalletCreditedQueue() {
        return QueueBuilder.durable(resellerWalletCreditedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerWithdrawalRequestedQueue() {
        return QueueBuilder.durable(resellerWithdrawalRequestedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    @Bean
    public Queue resellerWithdrawalProcessedQueue() {
        return QueueBuilder.durable(resellerWithdrawalProcessedQueue)
                .withArgument("x-dead-letter-exchange", resellerExchange + ".dlx")
                .build();
    }

    // Dead Letter Queue
    @Bean
    public Queue resellerDeadLetterQueue() {
        return QueueBuilder.durable(resellerExchange + ".dlq").build();
    }

    // Bindings
    @Bean
    public Binding resellerRegisteredBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerRegisteredQueue())
                .to(resellerExchange)
                .with(resellerRegisteredRoutingKey);
    }

    @Bean
    public Binding resellerApprovedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerApprovedQueue())
                .to(resellerExchange)
                .with(resellerApprovedRoutingKey);
    }

    @Bean
    public Binding resellerRejectedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerRejectedQueue())
                .to(resellerExchange)
                .with(resellerRejectedRoutingKey);
    }

    @Bean
    public Binding resellerSuspendedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerSuspendedQueue())
                .to(resellerExchange)
                .with(resellerSuspendedRoutingKey);
    }

    @Bean
    public Binding resellerOrderPlacedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerOrderPlacedQueue())
                .to(resellerExchange)
                .with(resellerOrderPlacedRoutingKey);
    }

    @Bean
    public Binding resellerWalletCreditedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerWalletCreditedQueue())
                .to(resellerExchange)
                .with(resellerWalletCreditedRoutingKey);
    }

    @Bean
    public Binding resellerWithdrawalRequestedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerWithdrawalRequestedQueue())
                .to(resellerExchange)
                .with(resellerWithdrawalRequestedRoutingKey);
    }

    @Bean
    public Binding resellerWithdrawalProcessedBinding(@Qualifier("resellerExchange") TopicExchange resellerExchange) {
        return BindingBuilder.bind(resellerWithdrawalProcessedQueue())
                .to(resellerExchange)
                .with(resellerWithdrawalProcessedRoutingKey);
    }

    @Bean
    public Binding resellerDeadLetterBinding() {
        return BindingBuilder.bind(resellerDeadLetterQueue())
                .to(resellerDeadLetterExchange())
                .with("#");
    }
}
