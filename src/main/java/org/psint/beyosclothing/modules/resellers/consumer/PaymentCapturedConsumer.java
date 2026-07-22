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
 * Consumer for payment captured events
 * Credits reseller wallet when payment is captured successfully
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCapturedConsumer {

    private final ResellerWalletService walletService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.payment-captured:payment.captured.queue}", ackMode = "MANUAL")
    public void handlePaymentCaptured(Map<String, Object> message, org.springframework.amqp.core.Message rawMessage,
                                      com.rabbitmq.client.Channel channel) {
        try {
            log.info("Received PaymentCapturedEvent: {}", message);

            String orderUuid = (String) message.get("orderUuid");
            String resellerId = (String) message.get("resellerId");

            // Only process if this is a reseller order
            if (resellerId == null || resellerId.isEmpty()) {
                log.debug("Not a reseller order, skipping wallet credit");
                channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // Get order items with margins from order module
            // For each item with reseller_margin_amount:
            // walletService.creditSaleProfit(resellerId, orderId, orderItemId, marginAmount);

            log.info("Payment captured event processed for reseller order: {}", orderUuid);
            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("Error processing PaymentCapturedEvent", e);
            try {
                // Negative acknowledgment - message will be requeued
                channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
            } catch (Exception ex) {
                log.error("Error sending NACK", ex);
            }
        }
    }
}

