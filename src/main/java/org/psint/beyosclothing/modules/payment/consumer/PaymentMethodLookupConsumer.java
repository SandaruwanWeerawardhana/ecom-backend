package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentMethodLookupRequest;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentMethodLookupResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.repository.PaymentMethodRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer for payment method lookup requests from Order module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentMethodLookupConsumer {

    private final PaymentMethodRepository paymentMethodRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.payment-method-lookup-request}")
    public PaymentMethodLookupResponse handlePaymentMethodLookupRequest(PaymentMethodLookupRequest request) {
        log.info("Received payment method lookup request - Request ID: {}, Method ID: {}",
                request.getRequestId(), request.getPaymentMethodId());

        try {
            PaymentMethodEntity paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId())
                    .orElse(null);

            if (paymentMethod == null) {
                log.warn("Payment method not found - ID: {}", request.getPaymentMethodId());
                return PaymentMethodLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .build();
            }

            log.info("Payment method found - ID: {}, Code: {}, Name: {}",
                    paymentMethod.getId(), paymentMethod.getCode(), paymentMethod.getName());

            return PaymentMethodLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .methodId(paymentMethod.getId())
                    .methodCode(paymentMethod.getCode())
                    .methodName(paymentMethod.getName())
                    .methodType(paymentMethod.getType().name())
                    .gatewayName(paymentMethod.getCode()) // Use code as gateway name (e.g., "onepay", "payhere")
                    .isActive(paymentMethod.getIsActive())
                    .build();

        } catch (Exception e) {
            log.error("Error looking up payment method - Request ID: {}", request.getRequestId(), e);
            return PaymentMethodLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .build();
        }
    }
}
