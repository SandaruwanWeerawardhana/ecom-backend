package org.psint.beyosclothing.modules.products.exception;

/**
 * Exception thrown when a business validation fails
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}

