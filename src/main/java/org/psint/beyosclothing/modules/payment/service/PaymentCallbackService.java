package org.psint.beyosclothing.modules.payment.service;

import java.util.Map;

/**
 * Handles server-to-server payment status callbacks from online gateways (e.g. OnePay).
 */
public interface PaymentCallbackService {

    /**
     * Process a callback payload from OnePay.
     * @param payload Raw callback body
     */
    void handleOnePayCallback(Map<String, Object> payload);

    /**
     * Re-verify the latest payment request of an order against the gateway's transaction status API.
     * Used as a customer-triggered fallback when the asynchronous gateway callback has not arrived.
     * On a newly confirmed terminal outcome (PAID/FAILED) the payment records are updated and the
     * "payment.status.updated" event is published so the order module applies the same finalization
     * as the callback path.
     * @param orderId Order ID whose payment should be verified
     * @return Result map: success (Boolean), paymentStatus (PAID/FAILED/PENDING), gatewayTransactionId, error
     */
    Map<String, Object> verifyOrderPaymentStatus(Long orderId);
}
