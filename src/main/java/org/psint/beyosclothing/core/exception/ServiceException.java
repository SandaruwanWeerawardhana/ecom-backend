package org.psint.beyosclothing.core.exception;

/**
 * Service Exception
 * Thrown when an internal service operation fails
 */
public class ServiceException extends RuntimeException {
    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

