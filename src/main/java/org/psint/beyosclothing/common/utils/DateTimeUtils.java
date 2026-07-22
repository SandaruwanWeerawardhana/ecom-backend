package org.psint.beyosclothing.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Date/Time Utility
 * Helper methods for date and time operations
 */
public class DateTimeUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMATTER) : null;
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : null;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private DateTimeUtils() {
        // Private constructor to prevent instantiation
    }
}

