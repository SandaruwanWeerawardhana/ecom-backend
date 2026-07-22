package org.psint.beyosclothing.modules.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.payment.service.PaymentCallbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Server-to-server payment status callbacks from online gateways.
 * These endpoints are called directly by the gateway (not the browser), so they
 * always return 200 to acknowledge receipt and avoid retry storms - failures are logged instead.
 */
@RestController
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Callbacks", description = "Server-to-server payment gateway callbacks")
public class PaymentCallbackController {

    private final PaymentCallbackService paymentCallbackService;

    @PostMapping("/onepay")
    @Operation(
            summary = "OnePay payment status callback",
            description = "Receives the server-to-server POST OnePay sends after a checkout attempt completes. " +
                    "Configure this URL (/api/v1/payments/callback/onepay) as the notify/callback URL in the OnePay merchant dashboard."
    )
    public ResponseEntity<Void> handleOnePayCallback(@RequestBody Map<String, Object> payload) {
        try {
            paymentCallbackService.handleOnePayCallback(payload);
        } catch (Exception e) {
            log.error("Unhandled error processing OnePay callback: {}", payload, e);
        }
        return ResponseEntity.ok().build();
    }
}
