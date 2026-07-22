package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.external.PaymentMethodLookupRequest;
import org.psint.beyosclothing.modules.delivery.dto.external.PaymentMethodLookupResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Payment Method Consumer
 * Handles cross-module payment method lookup requests from Delivery module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentMethodConsumer {

    private final PaymentMethodRepository paymentMethodRepository;
    /**
     * QUEUE: payment.method.lookup.request
     * Purpose: Lookup payment method by ID, UUID, or Code
     * Request: PaymentMethodLookupRequest
     * Response: PaymentMethodLookupResponse
     * Caller: ShippingCostCalculationServiceImpl
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.payment-method-lookup-request:payment.method.lookup.request}")
    public PaymentMethodLookupResponse handlePaymentMethodLookupRequest(PaymentMethodLookupRequest request) {
        log.debug("Payment Method Lookup Received request - ID: {}", request.getPaymentMethodId());

        try {
            PaymentMethodEntity paymentMethod = null;

            // Lookup by ID first
            if (request.getPaymentMethodId() != null) {
                paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId())
                        .orElse(null);
            }

            // If not found by ID, try UUID
            if (paymentMethod == null && request.getPaymentMethodUuid() != null) {
                paymentMethod = paymentMethodRepository.findByUuid(request.getPaymentMethodUuid())
                        .orElse(null);
            }

            // If still not found, try Code
            if (paymentMethod == null && request.getPaymentMethodCode() != null) {
                paymentMethod = paymentMethodRepository.findByCode(request.getPaymentMethodCode())
                        .orElse(null);
            }

            if (paymentMethod == null) {
                log.warn("Payment method not found - ID: {}, UUID: {}, Code: {}",
                        request.getPaymentMethodId(), request.getPaymentMethodUuid(), request.getPaymentMethodCode());
                return PaymentMethodLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("Payment method not found")
                        .build();
            }

            PaymentMethodLookupResponse response = PaymentMethodLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .paymentMethodId(paymentMethod.getId())
                    .paymentMethodUuid(paymentMethod.getUuid())
                    .paymentMethodCode(paymentMethod.getCode())
                    .paymentMethodName(paymentMethod.getName())
                    .paymentMethodType(paymentMethod.getType() != null ? paymentMethod.getType().name() : null)
                    .isActive(paymentMethod.getIsActive())
                    .isCourierFeeFree(paymentMethod.getIsCourierFeeFree())
                    .supportsRefund(paymentMethod.getSupportsRefund())
                    .supportsCallback(paymentMethod.getSupportsCallback())
                    .build();

            log.debug("Payment method lookup successful - ID: {}, Code: {}, isCourierFeeFree: {}",
                    paymentMethod.getId(), paymentMethod.getCode(), paymentMethod.getIsCourierFeeFree());

            return response;

        } catch (Exception e) {
            log.error("Error processing payment method lookup request", e);
            return PaymentMethodLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .errorMessage("Error looking up payment method: " + e.getMessage())
                    .build();
        }
    }
}
