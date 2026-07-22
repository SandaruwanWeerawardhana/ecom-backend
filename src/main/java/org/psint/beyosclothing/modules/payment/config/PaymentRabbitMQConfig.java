package org.psint.beyosclothing.modules.payment.config;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Payment Module
 * Ensures proper listener container factory for payment module consumers
 */
@Configuration
@EnableRabbit
public class PaymentRabbitMQConfig {

    /**
     * RabbitMQ Listener Container Factory for Payment Module
     * Uses payment transaction manager and JSON message converter
     */
    @Bean(name = "paymentRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory paymentRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("paymentMessageConverter") MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);

        return factory;
    }

    /**
     * JSON Message Converter for Payment Module
     */
    @Bean
    public MessageConverter paymentMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
