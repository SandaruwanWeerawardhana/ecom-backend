package org.psint.beyosclothing.modules.orders.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * RabbitMQ Configuration for Order Module
 * Ensures proper transaction management for order-related message consumers
 * Supports RPC (Request-Reply) pattern for order creation
 */
@Configuration
@EnableRabbit
public class OrderRabbitMQConfig {

    /**
     * RabbitMQ Listener Container Factory for Order Module
     * Uses the orderTransactionManager to ensure proper transaction boundaries
     * Configured to support RPC pattern with automatic reply handling
     */
    @Bean(name = "orderRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory orderRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("orderTransactionManager") PlatformTransactionManager orderTransactionManager,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setTransactionManager(orderTransactionManager);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);

        // Enable automatic reply handling for RPC pattern
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}
