package org.psint.beyosclothing.modules.delivery.config;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Delivery Module
 * Ensures proper listener container factory for delivery module consumers
 */
@Configuration
@EnableRabbit
public class DeliveryRabbitMQConfig {

    /**
     * RabbitMQ Listener Container Factory for Delivery Module
     * Uses default transaction manager and JSON message converter
     */
    @Bean(name = "deliveryRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory deliveryRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);

        return factory;
    }
}
