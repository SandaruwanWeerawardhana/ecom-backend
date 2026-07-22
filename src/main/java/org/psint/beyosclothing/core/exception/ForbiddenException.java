package org.psint.beyosclothing.core.exception;

/**
 * Forbidden Exception
 * Thrown when user doesn't have permission to access a resource
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}

