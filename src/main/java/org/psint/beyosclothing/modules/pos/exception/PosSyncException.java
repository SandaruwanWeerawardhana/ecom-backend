package org.psint.beyosclothing.modules.pos.exception;

/**
 * General POS synchronization exception for unrecoverable sync errors.
 */
public class PosSyncException extends RuntimeException {
    public PosSyncException(String message) { super(message); }
    public PosSyncException(String message, Throwable cause) { super(message, cause); }
}
