package org.psint.beyosclothing.modules.pos.exception;

/**
 * Exception thrown when attempting to checkout with an empty cart
 */
public class EmptyCartException extends RuntimeException {
    public EmptyCartException(String message) {
        super(message);
    }
}
