package org.psint.beyosclothing.common.utils;

import java.util.UUID;

/**
 * String Utility
 * Helper methods for string operations
 */
public class StringUtils {

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String generateUniqueId() {
        return UUID.randomUUID().toString();
    }

    public static String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }

    public static String generateInvoiceNumber() {
        return "INV-" + System.currentTimeMillis();
    }

    private StringUtils() {
        // Private constructor to prevent instantiation
    }
}

