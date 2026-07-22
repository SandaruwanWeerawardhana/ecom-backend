package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.service.PaymentCallbackService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RPC consumer for customer-triggered payment status re-verification.
 * The customer module sends the order ID; the latest payment request for that order is re-verified
 * against the gateway's transaction status API and the resulting payment status is returned.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStatusVerificationConsumer {

    private final PaymentCallbackService paymentCallbackService;

    /**
     * Request format: { requestId: String, orderId: Long }
     * Reply format: { requestId: String, success: Boolean, paymentStatus: String, gatewayTransactionId: String, error: String }
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.payment-status-verify-request:payment.status.verify.request.queue}")
    public Map<String, Object> handlePaymentStatusVerification(Map<String, Object> request) {
        String requestId = request.get("requestId") != null ? request.get("requestId").toString() : null;
        log.info("Received payment status verification request - Request ID: {}, Order ID: {}",
                requestId, request.get("orderId"));

        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);

        try {
            Long orderId = extractOrderId(request.get("orderId"));
            if (orderId == null) {
                result.put("success", false);
                result.put("error", "Missing orderId in payment status verification request");
                return result;
            }

            result.putAll(paymentCallbackService.verifyOrderPaymentStatus(orderId));
            return result;
        } catch (Exception e) {
            log.error("Error verifying payment status - Request ID: {}", requestId, e);
            result.put("success", false);
            result.put("error", "Payment status verification failed: " + e.getMessage());
            return result;
        }
    }

    private Long extractOrderId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
