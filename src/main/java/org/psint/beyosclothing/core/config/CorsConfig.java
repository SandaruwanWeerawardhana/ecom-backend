package org.psint.beyosclothing.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration
 * Configures Cross-Origin Resource Sharing for frontend applications
 * Allows credentials for HTTP-only cookie-based authentication
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials}")
    private boolean allowCredentials;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse and set allowed origins.
        // Use origin PATTERNS so wildcard subdomains (e.g. https://*.beyosclothing.com)
        // work together with allow-credentials=true, which forbids a plain "*" origin.
        configuration.setAllowedOriginPatterns(splitAndTrim(allowedOrigins));

        // Set allowed methods
        configuration.setAllowedMethods(splitAndTrim(allowedMethods));

        // Set allowed headers - must include Authorization for Bearer tokens
        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(List.of("*"));
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }

        // Allow credentials (required for HTTP-only cookies)
        configuration.setAllowCredentials(allowCredentials);

        // Expose headers that frontend can read
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));

        // Cache preflight for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Split a comma-separated config value into a trimmed, non-empty list.
     * Trimming prevents a stray space (e.g. "a.com, b.com") from producing an
     * origin that never matches.
     */
    private List<String> splitAndTrim(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
