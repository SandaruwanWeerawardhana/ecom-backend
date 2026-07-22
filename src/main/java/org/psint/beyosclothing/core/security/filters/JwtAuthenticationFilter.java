package org.psint.beyosclothing.core.security.filters;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.security.jwt.JwtService;
import org.psint.beyosclothing.modules.auth.dto.SessionData;
import org.psint.beyosclothing.modules.auth.service.RedisService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * JWT Authentication Filter
 * Intercepts requests and validates JWT tokens from:
 * 1. Authorization: Bearer <token> header (for Swagger/Postman)
 * 2. HTTP-only access_token cookie (for browser sessions)
 * Validates sessionId exists in Redis for single session enforcement
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RedisService redisService;

    // List of public endpoints that should skip JWT validation even if a token is present
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/login/cookie",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/dev/reset-password",
            "/api/v1/auth/health",
            "/api/v1/products/published",
            "/api/v1/cart/**",
            "/api/v1/cart/items",
            "/api/v1/cart/promo",
            "/api/v1/cart/merge",
            "/api/v1/products/filter",
            "/api/v1/products/shop/filter",
            "/api/v1/products/public/search",
            "/api/v1/products/variants/gallery",
            "/api/v1/products/slug",
            "/api/v1/products/categories/all",
            "/api/v1/checkout/prepare",
            "/api/v1/admin/reports/sales/export",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String requestPath = request.getRequestURI();
        String jwt = null;
        final String userEmail;

        // Skip JWT validation for public endpoints
        if (isPublicEndpoint(requestPath)) {
            log.debug("Skipping JWT validation for public endpoint: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Step 1: Try to extract JWT from Authorization header (Bearer token)
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            log.debug("JWT extracted from Authorization header");
        }

        // Step 2: If no Bearer token, try to extract from HTTP-only cookie
        if (jwt == null) {
            jwt = extractTokenFromCookie(request, "access_token");
            if (jwt != null) {
                log.debug("JWT extracted from access_token cookie");
            }
        }

        // If no token found, continue without authentication
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Check if token is blacklisted
            if (redisService.isTokenBlacklisted(jwt)) {
                log.warn("Attempt to use blacklisted token");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has been invalidated");
                return;
            }

            userEmail = jwtService.extractUsername(jwt);
            String sessionId = jwtService.extractSessionId(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    // Validate sessionId exists in Redis
                    if (sessionId != null) {
                        SessionData sessionData = redisService.getSession(sessionId);
                        if (sessionData == null) {
                            log.warn("Session not found in Redis for sessionId: {}", sessionId);
                            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Session expired or invalid");
                            return;
                        }

                        // Update last accessed time
                        sessionData.setLastAccessed(LocalDateTime.now());
                        redisService.updateSessionAccess(sessionId, sessionData, jwtService.getRefreshExpirationTime());
                    } else {
                        // Legacy token without sessionId - still validate via old Redis session
                        String storedToken = redisService.getJwtSession(userEmail);
                        if (storedToken == null || !storedToken.equals(jwt)) {
                            log.warn("Token not found in Redis session for user: {}", userEmail);
                            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Session expired or invalid");
                            return;
                        }
                    }

                    // Authentication successful
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has expired. Please login again.");
            return;
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token format");
            return;
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token signature");
            return;
        } catch (Exception e) {
            log.error("Error processing JWT token: {}", e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from HTTP-only cookie
     */
    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if the request path is a public endpoint
     */
    private boolean isPublicEndpoint(String requestPath) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(requestPath::startsWith);
    }

    /**
     * Send JSON error response
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                java.time.LocalDateTime.now(),
                status,
                status == 401 ? "Unauthorized" : "Forbidden",
                message
        ));
    }
}
