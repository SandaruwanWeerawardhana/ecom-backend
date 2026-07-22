package org.psint.beyosclothing.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.events.EmailVerificationEvent;
import org.psint.beyosclothing.modules.auth.events.PasswordResetEvent;
import org.psint.beyosclothing.modules.auth.events.SessionEvent;
import org.psint.beyosclothing.modules.auth.events.UserCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ Event Publisher Service
 * Publishes events to RabbitMQ queues
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.auth}")
    private String authExchange;

    @Value("${app.rabbitmq.routing-key.user-created}")
    private String userCreatedRoutingKey;

    @Value("${app.rabbitmq.routing-key.email-verification}")
    private String emailVerificationRoutingKey;

    @Value("${app.rabbitmq.routing-key.password-reset}")
    private String passwordResetRoutingKey;

    public void publishUserCreatedEvent(UserCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(authExchange, userCreatedRoutingKey, event);
            log.info("Published UserCreatedEvent for userId: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish UserCreatedEvent", e);
            throw new RuntimeException("Failed to publish user created event", e);
        }
    }

    public void publishEmailVerificationEvent(EmailVerificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(authExchange, emailVerificationRoutingKey, event, message -> {
                if ("HIGH".equals(event.getPriority())) {
                    message.getMessageProperties().setPriority(10);
                }
                return message;
            });
            log.info("Published EmailVerificationEvent for userId: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish EmailVerificationEvent", e);
            throw new RuntimeException("Failed to publish email verification event", e);
        }
    }

    public void publishPasswordResetEvent(PasswordResetEvent event) {
        try {
            rabbitTemplate.convertAndSend(authExchange, passwordResetRoutingKey, event, message -> {
                message.getMessageProperties().setPriority(10); // High priority
                return message;
            });
            log.info("Published PasswordResetEvent for userId: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish PasswordResetEvent", e);
            throw new RuntimeException("Failed to publish password reset event", e);
        }
    }

    /**
     * Publish session event (SESSION_CREATED, SESSION_REPLACED, SESSION_LOGGED_OUT)
     */
    public void publishSessionEvent(SessionEvent event) {
        try {
            // Use routing key based on event type: auth.session.created, auth.session.replaced, etc.
            String routingKey = "auth.session." + event.getEventType().toLowerCase().replace("_", ".");
            rabbitTemplate.convertAndSend(authExchange, routingKey, event);
            log.info("Published SessionEvent: {} for userId: {}", event.getEventType(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish SessionEvent: {}", event.getEventType(), e);
            // Don't throw - session events are for audit/monitoring, shouldn't break login flow
        }
    }
}
