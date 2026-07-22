package org.psint.beyosclothing.modules.payment.service;

import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayRequest;
import org.psint.beyosclothing.modules.payment.dto.gateway.PaymentGatewayResponse;

/**
 * Payment Gateway Service Interface
 * Defines contract for payment gateway integrations
 */
public interface PaymentGatewayService {

    /**
     * Initiate payment with gateway
     * @param request Payment gateway request
     * @return Payment gateway response with redirect URL
     */
    PaymentGatewayResponse initiatePayment(PaymentGatewayRequest request);

    /**
     * Verify payment callback from gateway
     * @param transactionId Gateway transaction ID
     * @param signature Payment signature for verification
     * @return Payment verification response
     */
    PaymentGatewayResponse verifyPayment(String transactionId, String signature);

    /**
     * Get gateway name
     * @return Gateway name
     */
    String getGatewayName();

    /**
     * Check if gateway is enabled
     * @return True if enabled
     */
    boolean isEnabled();
}

