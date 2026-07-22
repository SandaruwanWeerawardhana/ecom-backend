package org.psint.beyosclothing.modules.pos.exception;

/**
 * Exception thrown when card payment details are invalid
 */
public class InvalidCardDetailsException extends RuntimeException {
    public InvalidCardDetailsException(String message) {
        super(message);
    }
}
