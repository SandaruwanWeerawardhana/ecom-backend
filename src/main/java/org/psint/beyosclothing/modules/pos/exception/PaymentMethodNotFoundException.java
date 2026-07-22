package org.psint.beyosclothing.modules.pos.exception;

/**
 * Exception thrown when a payment method is not found or inactive
 */
public class PaymentMethodNotFoundException extends RuntimeException {
    public PaymentMethodNotFoundException(String message) {
        super(message);
    }
}
