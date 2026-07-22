package org.psint.beyosclothing.modules.delivery.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.external.PaymentMethodLookupRequest;
import org.psint.beyosclothing.modules.delivery.dto.external.PaymentMethodLookupResponse;
import org.psint.beyosclothing.modules.delivery.service.PaymentMethodLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementation of PaymentMethodLookupService
 * Uses RabbitMQ to communicate with Payment module
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentMethodLookupServiceImpl implements PaymentMethodLookupService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    private static final String PAYMENT_METHOD_LOOKUP_ROUTING_KEY = "payment.method.lookup.request";
    private static final int REPLY_TIMEOUT_MS = 5000;

    @Override
    public PaymentMethodLookupResponse lookupPaymentMethodById(Long paymentMethodId) {
        log.debug("Looking up payment method by ID: {}", paymentMethodId);

        PaymentMethodLookupRequest request = PaymentMethodLookupRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .paymentMethodId(paymentMethodId)
                .build();

        return sendLookupRequest(request);
    }

    @Override
    public PaymentMethodLookupResponse lookupPaymentMethodByUuid(String paymentMethodUuid) {
        log.debug("Looking up payment method by UUID: {}", paymentMethodUuid);

        PaymentMethodLookupRequest request = PaymentMethodLookupRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .paymentMethodUuid(paymentMethodUuid)
                .build();

        return sendLookupRequest(request);
    }

    @Override
    public PaymentMethodLookupResponse lookupPaymentMethodByCode(String paymentMethodCode) {
        log.debug("Looking up payment method by Code: {}", paymentMethodCode);

        PaymentMethodLookupRequest request = PaymentMethodLookupRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .paymentMethodCode(paymentMethodCode)
                .build();

        return sendLookupRequest(request);
    }

    @Override
    public boolean isCourierFeeFree(Long paymentMethodId) {
        log.debug("Checking if courier fee is free for payment method ID: {}", paymentMethodId);

        if (paymentMethodId == null) {
            return false;
        }

        PaymentMethodLookupResponse response = lookupPaymentMethodById(paymentMethodId);

        if (response == null || !response.isFound()) {
            log.warn("Payment method not found for ID: {}", paymentMethodId);
            return false;
        }

        boolean isFree = Boolean.TRUE.equals(response.getIsCourierFeeFree());
        log.debug("Payment method ID: {} has isCourierFeeFree: {}", paymentMethodId, isFree);

        return isFree;
    }

    private PaymentMethodLookupResponse sendLookupRequest(PaymentMethodLookupRequest request) {
        try {
            log.debug("Sending payment method lookup request to RabbitMQ - RequestId: {}", request.getRequestId());

            // Set timeout for reply
            rabbitTemplate.setReplyTimeout(REPLY_TIMEOUT_MS);

            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange,
                    PAYMENT_METHOD_LOOKUP_ROUTING_KEY,
                    request
            );

            if (response == null) {
                log.warn("No response received from payment module for request: {}", request.getRequestId());
                return PaymentMethodLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("Timeout waiting for payment module response")
                        .build();
            }

            if (response instanceof PaymentMethodLookupResponse lookupResponse) {
                log.debug("Received payment method lookup response - Found: {}, ID: {}",
                        lookupResponse.isFound(), lookupResponse.getPaymentMethodId());
                return lookupResponse;
            }

            log.error("Unexpected response type from payment module: {}", response.getClass().getName());
            return PaymentMethodLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .errorMessage("Unexpected response type from payment module")
                    .build();

        } catch (Exception e) {
            log.error("Error sending payment method lookup request via RabbitMQ", e);
            return PaymentMethodLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .errorMessage("Error communicating with payment module: " + e.getMessage())
                    .build();
        }
    }
}

