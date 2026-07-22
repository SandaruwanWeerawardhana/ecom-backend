package org.psint.beyosclothing.core.security;

import lombok.RequiredArgsConstructor;
import org.psint.beyosclothing.core.security.filters.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security Configuration
 * Configures JWT-based authentication and authorization
 * Supports both Bearer tokens and HTTP-only cookie authentication
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService; // ✅ Injected from UserDetailsServiceImpl

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                // Public authentication endpoints
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/login/cookie",
                    "/api/v1/auth/register",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/auth/dev/reset-password",
                    "/api/v1/auth/health",
                    "/api/v1/resellers/register",
                    "/api/v1/resellers/status",
                    "/api/v1/products/published/**",
                        "/api/v1/products/filter",
                        "/api/v1/products/shop/filter",
                        "/api/v1/products/public/search",
                        "/api/v1/products/variants/gallery/{uuid}",
                        "/api/v1/products/{uuid}",
                        "/api/v1/products/slug/{slug}",
                    "/api/v1/products/categories/all",
                    "/api/v1/cart/**",
                    "/api/v1/cart/items/**",
                    "/api/v1/cart/promo",
                    "/api/v1/cart/merge",
                    "/api/v1/checkout/prepare",
                    "/api/v1/admin/reports/sales/export",
                    "/api/sms/delivery-report",
                    "/api/v1/payments/callback/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/uploads/**",
                    // Swagger UI resources
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                // Admin endpoints - require ROLE_ADMIN (method-level @PreAuthorize handles specific permissions)
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Customer endpoints
                .requestMatchers("/api/v1/customers/**").hasAnyRole("CUSTOMER", "ADMIN")
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
