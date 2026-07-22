package org.psint.beyosclothing.modules.resellers.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for user created events from auth module
 * Validates user creation for reseller registration
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserCreatedConsumer {

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.admin-user-create:admin.user.create.queue}", ackMode = "MANUAL")
    public void handleUserCreated(Map<String, Object> message, org.springframework.amqp.core.Message rawMessage,
                                   com.rabbitmq.client.Channel channel) {
        try {
            log.info("Received UserCreatedEvent: {}", message);

            Long userId = ((Number) message.get("userId")).longValue();
            String userType = (String) message.get("userType");

            // Only process if this is a reseller user
            if (!"RESELLER".equals(userType)) {
                log.debug("Not a reseller user, skipping");
                channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            log.info("Reseller user created with ID: {}", userId);
            // The reseller entity should already be created by this point
            // This event is mainly for logging/audit purposes

            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("Error processing UserCreatedEvent", e);
            try {
                channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
            } catch (Exception ex) {
                log.error("Error sending NACK", ex);
            }
        }
    }
}

