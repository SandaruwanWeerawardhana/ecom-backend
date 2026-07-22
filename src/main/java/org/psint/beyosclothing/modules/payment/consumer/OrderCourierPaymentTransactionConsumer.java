package org.psint.beyosclothing.modules.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.entity.PaymentTransactionEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.psint.beyosclothing.modules.payment.repository.PaymentTransactionRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Consumer for creating payment transaction records when order is placed with courier
 * Creates a pending payment record for COD orders or payments to be collected
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCourierPaymentTransactionConsumer {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    /**
     * Listen for payment transaction creation events
     * Request: { "orderUuid": "xxx", "orderId": 123, "methodId": 456, "amount": 1000.00, "codAmount": 1000.00, "orderNumber": "ORD-123" }
     */
    @RabbitListener(queues = "order.courier.payment.transaction.create", ackMode = "AUTO")
    @Transactional("paymentTransactionManager")
    public void handlePaymentTransactionCreate(@Payload Map<String, Object> event) {
        log.info("=== ORDER COURIER PAYMENT TRANSACTION CREATE CONSUMER TRIGGERED ===");

        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            // Safely extract fields with null checks
            String orderUuid = (String) event.get("orderUuid");
            Object orderIdObj = event.get("orderId");
            Object amountObj = event.get("amount");
            Object codAmountObj = event.get("codAmount");
            String orderNumber = (String) event.get("orderNumber");

            // Validate required fields
            if (orderUuid == null || orderUuid.isEmpty()) {
                log.error("❌ Missing orderUuid in payment transaction event");
                return;
            }

            if (orderIdObj == null) {
                log.error("❌ Missing orderId in payment transaction event for order: {}", orderUuid);
                return;
            }

            if (amountObj == null) {
                log.error("❌ Missing amount in payment transaction event for order: {}", orderUuid);
                return;
            }

            // Convert to proper types
            Long orderId = ((Number) orderIdObj).longValue();
            BigDecimal amount = new BigDecimal(amountObj.toString());
            BigDecimal codAmount = codAmountObj != null ?
                    new BigDecimal(codAmountObj.toString()) : amount;

            log.info("Creating payment transaction for order {} (ID: {}) - Amount: {}, COD: {}",
                    orderNumber, orderId, amount, codAmount);

            Optional<PaymentMethodEntity> paymentMethodEntity = paymentMethodRepository.findByCode("COD");


            // Create payment transaction record (PENDING status - customer hasn't paid yet)
            PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                    .uuid(java.util.UUID.randomUUID().toString())
                    .orderId(orderId)
                    .methodId(paymentMethodEntity.get().getId()) // COD payments don't have a payment method yet - will be set when customer pays
                    .amount(codAmount)
                    .currency("LKR")
                    .status(PaymentTransactionEntity.TransactionStatus.PENDING)
                    .statusMessage("Pending - Order placed with courier for COD collection")
                    .paymentRequestId(null) // No payment request yet - customer will pay to courier
                    .gatewayTransactionId(null)
                    .callbackPayload(null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            PaymentTransactionEntity savedTransaction = paymentTransactionRepository.save(transaction);
            log.info("✅ Payment transaction created with UUID: {} for order {} (UUID: {})",
                    savedTransaction.getUuid(), orderNumber, orderUuid);

        } catch (Exception e) {
            log.error("❌ Error in payment transaction create consumer: {}", e.getMessage(), e);
        }
    }
}
