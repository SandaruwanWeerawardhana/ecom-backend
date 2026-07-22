package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.entity.PaymentTransactionEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentTransactionRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consumer for delivery confirmed payment transaction updates
 * Updates payment status when shipment is confirmed delivered by courier
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CourierDeliveryPaymentTransactionConsumer {

    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * Handle payment transaction update for delivery confirmation
     * Event format: {
     *   orderId: Long,
     *   orderUuid: String,
     *   orderNumber: String,
     *   paymentStatus: String,
     *   paymentReference: String,
     *   shipmentUuid: String,
     *   waybillId: String,
     *   deliveredAt: LocalDateTime,
     *   codAmount: String,
     *   deliveryCharge: String,
     *   eventSource: String,
     *   timestamp: Long
     * }
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.payment-transaction-delivery-confirmed:payment.transaction.delivery.confirmed}",
            containerFactory = "paymentRabbitListenerContainerFactory"
    )
    @Transactional(value = "paymentTransactionManager")
    public void handleDeliveryConfirmedPaymentUpdate(Map<String, Object> event) {
        try {
            log.info("💳 Received delivery confirmed payment transaction update event: {}", event);

            Long orderId = null;
            String orderUuid = (String) event.get("orderUuid");
            String orderNumber = (String) event.get("orderNumber");
            String paymentReference = (String) event.get("paymentReference");
            String shipmentUuid = (String) event.get("shipmentUuid");
            String waybillId = (String) event.get("waybillId");

            Object orderIdObj = event.get("orderId");
            if (orderIdObj instanceof Number) {
                orderId = ((Number) orderIdObj).longValue();
            }

            if (orderId == null) {
                log.warn("⚠️ Missing orderId in delivery confirmed payment event");
                return;
            }

            log.info("💰 Processing payment transaction update: OrderId={}, OrderNumber={}, PaymentRef={}",
                    orderId, orderNumber, paymentReference);

            // Find payment transaction by orderId
            var paymentTransactions = paymentTransactionRepository.findByOrderId(orderId);

            if (paymentTransactions == null || paymentTransactions.isEmpty()) {
                log.warn("⚠️ No payment transaction found for orderId: {}", orderId);
                return;
            }

            // Update all payment transactions for this order to SUCCESS status
            for (PaymentTransactionEntity transaction : paymentTransactions) {
                // Only update if not already successful
                if (transaction.getStatus() != PaymentTransactionEntity.TransactionStatus.SUCCESS) {
                    transaction.setStatus(PaymentTransactionEntity.TransactionStatus.SUCCESS);
                    transaction.setStatusMessage("Payment captured - Delivery confirmed by courier");
                    transaction.setPaidAt(LocalDateTime.now());

                    paymentTransactionRepository.save(transaction);

                    log.info("✅ Payment transaction status marked as SUCCESS for orderId: {}, transactionUuid: {}",
                            orderId, transaction.getUuid());
                }
            }

            log.info("✅ All payment transactions updated for order: {}", orderId);

        } catch (Exception e) {
            log.error("❌ Error handling delivery confirmed payment transaction update: {}", e.getMessage(), e);
        }
    }
}
