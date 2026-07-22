package org.psint.beyosclothing.modules.delivery.service;

import org.psint.beyosclothing.modules.delivery.dto.external.PaymentMethodLookupResponse;

/**
 * Service interface for looking up payment method details via RabbitMQ
 */
public interface PaymentMethodLookupService {

    /**
     * Lookup payment method by ID
     * @param paymentMethodId Payment method ID
     * @return PaymentMethodLookupResponse with payment method details
     */
    PaymentMethodLookupResponse lookupPaymentMethodById(Long paymentMethodId);

    /**
     * Lookup payment method by UUID
     * @param paymentMethodUuid Payment method UUID
     * @return PaymentMethodLookupResponse with payment method details
     */
    PaymentMethodLookupResponse lookupPaymentMethodByUuid(String paymentMethodUuid);

    /**
     * Lookup payment method by Code
     * @param paymentMethodCode Payment method code (e.g., "COD", "CARD")
     * @return PaymentMethodLookupResponse with payment method details
     */
    PaymentMethodLookupResponse lookupPaymentMethodByCode(String paymentMethodCode);

    /**
     * Check if payment method provides free courier fee
     * @param paymentMethodId Payment method ID
     * @return true if courier fee is free, false otherwise
     */
    boolean isCourierFeeFree(Long paymentMethodId);
}

