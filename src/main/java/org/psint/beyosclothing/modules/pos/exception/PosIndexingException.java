package org.psint.beyosclothing.modules.pos.exception;

/**
 * Exception thrown when Elasticsearch indexing/deletion fails.
 * Typically used for logging/metrics; not thrown upstream to avoid failing the listener.
 */
public class PosIndexingException extends RuntimeException {
    public PosIndexingException(String message) { super(message); }
    public PosIndexingException(String message, Throwable cause) { super(message, cause); }
}
