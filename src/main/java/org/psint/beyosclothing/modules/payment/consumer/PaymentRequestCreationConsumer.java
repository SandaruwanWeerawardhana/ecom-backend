package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentRequestCreationRequest;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentRequestCreationResponse;
import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayRequest;
import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.entity.PaymentRequestEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.psint.beyosclothing.modules.payment.repository.PaymentRequestRepository;
import org.psint.beyosclothing.modules.payment.service.PaymentGatewayService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consumer for payment request creation from Order module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestCreationConsumer {

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final List<PaymentGatewayService> gatewayServices; // Auto-wired list of all gateway implementations

    @RabbitListener(queues = "${app.rabbitmq.queue.payment-request-create-request}")
    @Transactional
    public PaymentRequestCreationResponse handlePaymentRequestCreation(PaymentRequestCreationRequest request) {
        log.info("Received payment request creation - Request ID: {}, Order ID: {}, Method ID: {}",
                request.getRequestId(), request.getOrderId(), request.getMethodId());

        try {
            // Fetch payment method
            PaymentMethodEntity paymentMethod = paymentMethodRepository.findById(request.getMethodId())
                    .orElse(null);

            if (paymentMethod == null) {
                log.warn("Payment method not found - ID: {}", request.getMethodId());
                return PaymentRequestCreationResponse.builder()
                        .requestId(request.getRequestId())
                        .success(false)
                        .errorMessage("Payment method not found")
                        .build();
            }

            // Create payment request entity
            PaymentRequestEntity paymentRequest = PaymentRequestEntity.builder()
                    .uuid(generatePaymentReference())
                    .orderId(request.getOrderId())
                    .methodId(request.getMethodId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(PaymentRequestEntity.PaymentRequestStatus.PENDING)
                    .build();

            // Determine if online gateway is needed
            String redirectUrl = null;
            if (isOnlineGateway(paymentMethod.getType())) {
                // Process online payment gateway
                PaymentGatewayResponse gatewayResponse = initiateGatewayPayment(
                        paymentMethod, paymentRequest, request);

                if (gatewayResponse != null && gatewayResponse.getSuccess()) {
                    paymentRequest.setGatewayTransactionId(gatewayResponse.getTransactionId());
                    paymentRequest.setRedirectUrl(gatewayResponse.getRedirectUrl());
                    paymentRequest.setStatus(PaymentRequestEntity.PaymentRequestStatus.REDIRECTED);
                    redirectUrl = gatewayResponse.getRedirectUrl();

                    log.info("Gateway payment initiated - Transaction ID: {}", gatewayResponse.getTransactionId());
                } else {
                    // Online payment could not be initiated: persist as FAILED and report it so the caller
                    // is not left presenting an online checkout with no gateway redirect URL.
                    String reason = gatewayResponse != null ? gatewayResponse.getErrorMessage() : "Payment gateway unavailable";
                    log.warn("Gateway payment initiation failed - Method: {}, Reason: {}", paymentMethod.getName(), reason);
                    paymentRequest.setStatus(PaymentRequestEntity.PaymentRequestStatus.FAILED);
                    paymentRequest = paymentRequestRepository.save(paymentRequest);
                    return PaymentRequestCreationResponse.builder()
                            .requestId(request.getRequestId())
                            .success(false)
                            .paymentRequestId(paymentRequest.getId())
                            .paymentRequestUuid(paymentRequest.getUuid())
                            .status(paymentRequest.getStatus().name())
                            .errorMessage("Payment initiation failed: " + reason)
                            .build();
                }
            }

            // Save payment request
            paymentRequest = paymentRequestRepository.save(paymentRequest);

            log.info("Payment request created - ID: {}, UUID: {}, Status: {}",
                    paymentRequest.getId(), paymentRequest.getUuid(), paymentRequest.getStatus());

            return PaymentRequestCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(true)
                    .paymentRequestId(paymentRequest.getId())
                    .paymentRequestUuid(paymentRequest.getUuid())
                    .status(paymentRequest.getStatus().name())
                    .redirectUrl(redirectUrl)
                    .gatewayTransactionId(paymentRequest.getGatewayTransactionId())
                    .build();

        } catch (Exception e) {
            log.error("Error creating payment request - Request ID: {}", request.getRequestId(), e);
            return PaymentRequestCreationResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("Error creating payment request: " + e.getMessage())
                    .build();
        }
    }

    private boolean isOnlineGateway(PaymentMethodEntity.PaymentType paymentType) {
        return paymentType == PaymentMethodEntity.PaymentType.ONLINE;
    }

    /**
     * Generates the payment request identifier that is also sent to the gateway as its "reference"
     * and echoed back on the payment callback for correlation. OnePay caps the reference at 21
     * characters, so a full 36-character UUID cannot be used; a compact 20-character token keeps it
     * within the limit while remaining unique enough for the payment_requests table.
     */
    private String generatePaymentReference() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private PaymentGatewayResponse initiateGatewayPayment(PaymentMethodEntity paymentMethod,
                                                           PaymentRequestEntity paymentRequest,
                                                           PaymentRequestCreationRequest request) {
        try {
            // Find appropriate gateway service based on payment method code
            // For example: "onepay" code maps to OnePay gateway service
            PaymentGatewayService gatewayService = gatewayServices.stream()
                    .filter(service -> service.getGatewayName().equalsIgnoreCase(paymentMethod.getCode()))
                    .filter(PaymentGatewayService::isEnabled)
                    .findFirst()
                    .orElse(null);

            if (gatewayService == null) {
                log.warn("No gateway service found for payment method code: {}", paymentMethod.getCode());
                return null;
            }

            // Build gateway request with actual order number
            PaymentGatewayRequest gatewayRequest = PaymentGatewayRequest.builder()
                    .orderId(request.getOrderId())
                    .orderUuid(request.getOrderUuid()) // ✅ Pass order UUID
                    .orderNumber(request.getOrderNumber()) // ✅ Use actual order number from request
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .returnUrl(request.getReturnUrl())
                    .cancelUrl(request.getCancelUrl())
                    .customerFirstName(request.getCustomerFirstName())
                    .customerLastName(request.getCustomerLastName())
                    .customerEmail(request.getCustomerEmail())
                    .customerPhone(request.getCustomerPhone())
                    .paymentRequestUuid(paymentRequest.getUuid())
                    .build();

            // Initiate payment with gateway
            return gatewayService.initiatePayment(gatewayRequest);

        } catch (Exception e) {
            log.error("Error initiating gateway payment", e);
            return null;
        }
    }
}
