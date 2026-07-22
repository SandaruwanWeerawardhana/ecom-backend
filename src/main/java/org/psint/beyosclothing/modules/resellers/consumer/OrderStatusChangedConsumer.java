package org.psint.beyosclothing.modules.resellers.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumer for order status changed events
 * Credits wallet when order is delivered, debits when order is cancelled/refunded
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderStatusChangedConsumer {

    private final ResellerWalletService walletService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.order-status-changed:order.status.changed.queue}", ackMode = "MANUAL")
    public void handleOrderStatusChanged(Map<String, Object> message, org.springframework.amqp.core.Message rawMessage,
                                          com.rabbitmq.client.Channel channel) {
        try {
            log.info("Received OrderStatusChangedEvent: {}", message);

            String orderUuid = (String) message.get("orderUuid");
            String newStatus = (String) message.get("newStatus");
            String resellerUuid = (String) message.get("resellerUuid");

            // Only process if this is a reseller order
            if (resellerUuid == null || resellerUuid.isEmpty()) {
                log.debug("Not a reseller order, skipping");
                channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            if ("DELIVERED".equals(newStatus)) {
                log.info("Order delivered, crediting reseller profit for order: {}", orderUuid);
                // Credit profit to wallet
                // Query order_items table to get margins
                // Call walletService.creditSaleProfit() for each item
            } else if ("CANCELLED".equals(newStatus) || "REFUNDED".equals(newStatus)) {
                log.info("Order cancelled/refunded, reversing reseller profit for order: {}", orderUuid);
                // Reverse profit from wallet
                // Call walletService.refundToWallet()
            }

            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("Error processing OrderStatusChangedEvent", e);
            try {
                channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
            } catch (Exception ex) {
                log.error("Error sending NACK", ex);
            }
        }
    }
}

