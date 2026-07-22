package org.psint.beyosclothing.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentRequestEntity;
import org.psint.beyosclothing.modules.payment.entity.PaymentTransactionEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentRequestRepository;
import org.psint.beyosclothing.modules.payment.repository.PaymentTransactionRepository;
import org.psint.beyosclothing.modules.payment.service.PaymentCallbackService;
import org.psint.beyosclothing.modules.payment.service.PaymentGatewayService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles OnePay's server-to-server payment status callback.
 * Correlation with the originating attempt happens primarily via the "additional_data" field, which
 * OnePay echoes back from the "additionalData" we send at checkout-link creation (set to our
 * payment_requests.uuid). The v3 callback does not include the "reference" field, so that and the
 * gateway "transaction_id" are only best-effort fallbacks. A callback that matches none of them is
 * logged with the full raw payload rather than silently dropped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackServiceImpl implements PaymentCallbackService {

    private static final String ONEPAY_GATEWAY_NAME = "OnePay";

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final List<PaymentGatewayService> gatewayServices;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    @Override
    @Transactional
    public void handleOnePayCallback(Map<String, Object> payload) {
        log.info("Received OnePay callback: {}", payload);

        String transactionId = extractString(payload, "transaction_id", "transactionId");
        String statusMessage = extractString(payload, "status_message", "statusMessage");
        String additionalData = extractString(payload, "additional_data", "additionalData");
        String reference = extractString(payload, "reference", "reference");
        boolean callbackReportsSuccess = isSuccessStatus(extractRaw(payload, "status", "status"), statusMessage);

        PaymentRequestEntity paymentRequest = correlatePaymentRequest(additionalData, reference, transactionId);

        if (paymentRequest == null) {
            log.warn("Could not correlate OnePay callback to a payment request - additionalData: {}, reference: {}, transactionId: {}, full payload: {}",
                    additionalData, reference, transactionId, payload);
            return;
        }

        boolean success = callbackReportsSuccess;
        PaymentGatewayResponse verification = verifyWithGateway(transactionId);
        if (verification != null && Boolean.FALSE.equals(verification.getSuccess()) && "FAILED".equalsIgnoreCase(verification.getStatus())) {
            // Explicit contradiction from the status endpoint - prefer the safer outcome
            success = false;
        }

        applyPaymentOutcome(paymentRequest, success, transactionId, statusMessage, payload);

        log.info("OnePay callback processed - PaymentRequest UUID: {}, OrderId: {}, Success: {}",
                paymentRequest.getUuid(), paymentRequest.getOrderId(), success);
    }

    @Override
    @Transactional
    public Map<String, Object> verifyOrderPaymentStatus(Long orderId) {
        PaymentRequestEntity paymentRequest = paymentRequestRepository.findByOrderId(orderId).stream()
                .max(Comparator.comparing(PaymentRequestEntity::getId))
                .orElse(null);

        if (paymentRequest == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "No payment request found for order ID: " + orderId);
            return result;
        }

        // Only a still-open request with a known gateway transaction can be re-verified; settled
        // requests and offline methods (no gateway transaction) just report their current status.
        boolean verifiable = !isSettled(paymentRequest.getStatus())
                && paymentRequest.getGatewayTransactionId() != null;

        if (verifiable) {
            String transactionId = paymentRequest.getGatewayTransactionId();
            PaymentGatewayResponse verification = verifyWithGateway(transactionId);

            if (verification != null && Boolean.TRUE.equals(verification.getSuccess())
                    && "PAID".equalsIgnoreCase(verification.getStatus())) {
                applyPaymentOutcome(paymentRequest, true, transactionId, "Verified via gateway status API", null);
            } else if (verification != null && Boolean.FALSE.equals(verification.getSuccess())
                    && "FAILED".equalsIgnoreCase(verification.getStatus())) {
                applyPaymentOutcome(paymentRequest, false, transactionId, "Verified via gateway status API", null);
            }
            // Transport-level verification errors leave the request open; a later callback or poll settles it.
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("paymentStatus", toReportableStatus(paymentRequest.getStatus()));
        result.put("gatewayTransactionId", paymentRequest.getGatewayTransactionId());
        return result;
    }

    private boolean isSettled(PaymentRequestEntity.PaymentRequestStatus status) {
        return status == PaymentRequestEntity.PaymentRequestStatus.PAID
                || status == PaymentRequestEntity.PaymentRequestStatus.FAILED;
    }

    /**
     * Maps the internal payment request status to the status vocabulary exposed to clients:
     * PENDING/REDIRECTED are both reported as PENDING; terminal states pass through unchanged.
     */
    private String toReportableStatus(PaymentRequestEntity.PaymentRequestStatus status) {
        if (status == PaymentRequestEntity.PaymentRequestStatus.PENDING
                || status == PaymentRequestEntity.PaymentRequestStatus.REDIRECTED) {
            return "PENDING";
        }
        return status.name();
    }

    /**
     * Persists a terminal payment outcome (request + transaction records) and publishes the
     * "payment.status.updated" event that drives the order module's payment update/finalization.
     * Shared by the gateway callback path and the customer-triggered re-verification path.
     */
    private void applyPaymentOutcome(PaymentRequestEntity paymentRequest, boolean success,
                                     String transactionId, String statusMessage, Map<String, Object> callbackPayload) {
        paymentRequest.setGatewayTransactionId(transactionId);
        paymentRequest.setStatus(success ? PaymentRequestEntity.PaymentRequestStatus.PAID : PaymentRequestEntity.PaymentRequestStatus.FAILED);
        final PaymentRequestEntity resolvedPaymentRequest = paymentRequestRepository.save(paymentRequest);

        PaymentTransactionEntity transaction = paymentTransactionRepository
                .findByPaymentRequestId(resolvedPaymentRequest.getId())
                .orElseGet(() -> PaymentTransactionEntity.builder()
                        .uuid(UUID.randomUUID().toString())
                        .paymentRequestId(resolvedPaymentRequest.getId())
                        .methodId(resolvedPaymentRequest.getMethodId())
                        .orderId(resolvedPaymentRequest.getOrderId())
                        .amount(resolvedPaymentRequest.getAmount())
                        .currency(resolvedPaymentRequest.getCurrency())
                        .build());

        transaction.setGatewayTransactionId(transactionId);
        transaction.setStatus(success ? PaymentTransactionEntity.TransactionStatus.SUCCESS : PaymentTransactionEntity.TransactionStatus.FAILED);
        transaction.setStatusMessage(statusMessage);
        transaction.setPaidAt(success ? LocalDateTime.now() : null);
        if (callbackPayload != null) {
            transaction.setCallbackPayload(callbackPayload);
        }
        paymentTransactionRepository.save(transaction);

        if (paymentRequest.getOrderId() != null) {
            publishPaymentStatusUpdate(paymentRequest.getOrderId(), success, transactionId);
        }
    }

    /**
     * Correlates a callback to its payment request. Prefers the echoed additional_data (our
     * payment_requests.uuid), then the reference field (only some setups echo it), then the gateway
     * transaction id captured at checkout-link creation.
     */
    private PaymentRequestEntity correlatePaymentRequest(String additionalData, String reference, String transactionId) {
        PaymentRequestEntity paymentRequest = findByUuidIfPresent(additionalData);
        if (paymentRequest == null) {
            paymentRequest = findByUuidIfPresent(reference);
        }
        if (paymentRequest == null && transactionId != null) {
            paymentRequest = paymentRequestRepository.findByGatewayTransactionId(transactionId).orElse(null);
        }
        return paymentRequest;
    }

    private PaymentRequestEntity findByUuidIfPresent(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return paymentRequestRepository.findByUuid(uuid).orElse(null);
    }

    private PaymentGatewayResponse verifyWithGateway(String transactionId) {
        if (transactionId == null) {
            return null;
        }
        return gatewayServices.stream()
                .filter(service -> ONEPAY_GATEWAY_NAME.equalsIgnoreCase(service.getGatewayName()))
                .findFirst()
                .map(service -> service.verifyPayment(transactionId, null))
                .orElse(null);
    }

    private void publishPaymentStatusUpdate(Long orderId, boolean success, String gatewayTransactionId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", orderId);
            event.put("paymentStatus", success ? "PAID" : "FAILED");
            event.put("gatewayTransactionId", gatewayTransactionId);

            rabbitTemplate.convertAndSend(paymentExchange, "payment.status.updated", event);
            log.info("Published payment.status.updated event - OrderId: {}, Status: {}", orderId, success ? "PAID" : "FAILED");
        } catch (Exception e) {
            log.error("Failed to publish payment status update for orderId: {}", orderId, e);
        }
    }

    private boolean isSuccessStatus(Object status, String statusMessage) {
        if (status != null && ("1".equals(String.valueOf(status)) || "true".equalsIgnoreCase(String.valueOf(status)))) {
            return true;
        }
        return statusMessage != null && "SUCCESS".equalsIgnoreCase(statusMessage.trim());
    }


    private String extractString(Map<String, Object> payload, String snakeCaseKey, String camelCaseKey) {
        return stringOrNull(extractRaw(payload, snakeCaseKey, camelCaseKey));
    }


    private Object extractRaw(Map<String, Object> payload, String snakeCaseKey, String camelCaseKey) {
        Object value = firstNonNull(payload.get(snakeCaseKey), payload.get(camelCaseKey));
        if (value != null) {
            return value;
        }
        Map<String, Object> data = asMap(payload.get("data"));
        return data != null ? firstNonNull(data.get(snakeCaseKey), data.get(camelCaseKey)) : null;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() || "null".equals(str) ? null : str;
    }
}
