package org.psint.beyosclothing.modules.resellers.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for product price updated events
 * Creates audit log when reseller prices are updated
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductPriceUpdatedConsumer {

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.product-price-changed:product.price.changed.queue}", ackMode = "MANUAL")
    public void handleProductPriceUpdated(Map<String, Object> message, org.springframework.amqp.core.Message rawMessage,
                                           com.rabbitmq.client.Channel channel) {
        try {
            log.info("Received ProductPriceUpdatedEvent: {}", message);

            String productUuid = (String) message.get("productUuid");
            String variantUuid = (String) message.get("variantUuid");
            Object oldResellerPrice = message.get("oldResellerPrice");
            Object newResellerPrice = message.get("newResellerPrice");

            // Create audit log entry for reseller price change
            if (oldResellerPrice != null || newResellerPrice != null) {
                log.info("Reseller price changed for product: {} variant: {} - Old: {} New: {}",
                        productUuid, variantUuid, oldResellerPrice, newResellerPrice);

                // TODO: Create audit entry in reseller_pricing_audit table
                // TODO: Optionally notify affected resellers
            }

            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("Error processing ProductPriceUpdatedEvent", e);
            try {
                channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
            } catch (Exception ex) {
                log.error("Error sending NACK", ex);
            }
        }
    }
}

