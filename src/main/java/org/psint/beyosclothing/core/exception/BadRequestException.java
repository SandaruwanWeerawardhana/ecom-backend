package org.psint.beyosclothing.core.exception;

/**
 * Bad Request Exception
 * Thrown when request data is invalid
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}

