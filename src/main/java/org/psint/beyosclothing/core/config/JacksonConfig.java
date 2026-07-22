package org.psint.beyosclothing.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Jackson Configuration
 * Configures JSON serialization/deserialization settings
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Register Java 8 Date/Time module
        objectMapper.registerModule(new JavaTimeModule());

        // Disable writing dates as timestamps
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Enable pretty printing for development
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Don't fail on unknown properties
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Set date format using AppConstants
        SimpleDateFormat dateFormat = new SimpleDateFormat(AppConstants.DATETIME_FORMAT + ".SSS'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        objectMapper.setDateFormat(dateFormat);

        // Set timezone
        objectMapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        return objectMapper;
    }
}
