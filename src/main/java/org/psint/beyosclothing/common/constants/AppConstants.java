package org.psint.beyosclothing.common.constants;

/**
 * Application Constants
 * Centralized constants used across the application
 */
public class AppConstants {

    // API Version
    public static final String API_VERSION = "/api/v1";

    // Pagination
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "20";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";

    // Date Format
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    // Cache Names
    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_USER_DETAILS = "userDetails";
    public static final String CACHE_INVENTORY = "inventory";

    // Queue Names
    public static final String ORDER_QUEUE = "order-queue";
    public static final String PAYMENT_QUEUE = "payment-queue";
    public static final String INVENTORY_QUEUE = "inventory-queue";
    public static final String EMAIL_QUEUE = "email-queue";

    // File Upload
    public static final long MAX_FILE_SIZE = 10485760; // 10MB
    public static final String[] ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/jpg"};

    // Regex Patterns
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";
    public static final String PHONE_PATTERN = "^[0-9]{10,15}$";

    private AppConstants() {
        // Private constructor to prevent instantiation
    }
}

