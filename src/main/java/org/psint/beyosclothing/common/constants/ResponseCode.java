package org.psint.beyosclothing.common.constants;

/**
 * Standardized Response Codes
 * Used across all API responses for consistent client handling
 */
public enum ResponseCode {

    // Success Codes (1000-1099)
    SUCCESS(1000, "Operation successful"),
    CREATED(1001, "Resource created successfully"),
    UPDATED(1002, "Resource updated successfully"),
    DELETED(1003, "Resource deleted successfully"),

    // Client Error Codes (1100-1199)
    BAD_REQUEST(1100, "Invalid request parameters"),
    VALIDATION_ERROR(1101, "Validation failed"),
    UNAUTHORIZED(1102, "Authentication required"),
    FORBIDDEN(1103, "Access denied - insufficient permissions"),
    NOT_FOUND(1104, "Resource not found"),
    CONFLICT(1105, "Resource already exists"),
    RATE_LIMIT_EXCEEDED(1106, "Too many requests"),

    // Authentication Error Codes (1200-1299)
    INVALID_CREDENTIALS(1200, "Invalid email or password"),
    ACCOUNT_LOCKED(1201, "Account is locked"),
    EMAIL_NOT_VERIFIED(1202, "Email not verified"),
    TOKEN_EXPIRED(1203, "Token has expired"),
    INVALID_TOKEN(1204, "Invalid token"),

    // Authorization Error Codes (1300-1399)
    PERMISSION_DENIED(1300, "You don't have permission to perform this action"),
    ROLE_NOT_FOUND(1301, "Role not found"),
    INVALID_ROLE(1302, "Invalid role assignment"),

    // Server Error Codes (1500-1599)
    INTERNAL_ERROR(1500, "Internal server error"),
    DATABASE_ERROR(1501, "Database operation failed"),
    SERVICE_UNAVAILABLE(1502, "Service temporarily unavailable"),

    // Business Logic Error Codes (1600-1699)
    INSUFFICIENT_INVENTORY(1600, "Insufficient inventory"),
    ORDER_ALREADY_PROCESSED(1601, "Order already processed"),
    PAYMENT_FAILED(1602, "Payment processing failed");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

