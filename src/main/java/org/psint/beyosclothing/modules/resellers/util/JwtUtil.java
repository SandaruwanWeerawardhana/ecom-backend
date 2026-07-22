package org.psint.beyosclothing.modules.resellers.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Utility class for extracting user information from JWT tokens
 * Works with UsernamePasswordAuthenticationToken and JJWT library
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    private final UserRepository userRepository;

    /**
     * Extract user ID from JWT authentication
     *
     * @param authentication Spring Security Authentication object
     * @return User ID from database lookup using email
     * @throws IllegalArgumentException if authentication is invalid or user not found
     */
    public static Long extractUserId(Authentication authentication, UserRepository userRepository) {
        if (authentication == null) {
            throw new IllegalArgumentException("Authentication is null");
        }

        try {
            // Get email from authentication principal
            String email = extractEmail(authentication);

            // Look up user ID from database
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

            return user.getId();

        } catch (Exception e) {
            log.error("Failed to extract user ID from authentication", e);
            throw new IllegalArgumentException("Failed to extract user ID from authentication", e);
        }
    }

    /**
     * Extract user ID from JWT authentication (non-static version for component injection)
     *
     * @param authentication Spring Security Authentication object
     * @return User ID from database lookup using email
     */
    public Long getUserId(Authentication authentication) {
        return extractUserId(authentication, userRepository);
    }

    /**
     * Extract email from JWT authentication
     *
     * @param authentication Spring Security Authentication object
     * @return Email from authentication principal
     */
    public static String extractEmail(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("Authentication is null");
        }

        try {
            // The principal is a UserDetails object with email as username
            Object principal = authentication.getPrincipal();

            if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername(); // Username is the email
            }

            // Fallback to getName()
            return authentication.getName();

        } catch (Exception e) {
            log.error("Failed to extract email from authentication", e);
            throw new IllegalArgumentException("Failed to extract email from authentication", e);
        }
    }

    /**
     * Extract user type from JWT authentication authorities
     *
     * @param authentication Spring Security Authentication object
     * @return User type (ADMIN, CUSTOMER, RESELLER) from authorities
     */
    public static String extractUserType(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("Authentication is null");
        }

        try {
            // User type is stored as ROLE_ADMIN, ROLE_CUSTOMER, ROLE_RESELLER
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String auth = authority.getAuthority();
                if (auth.startsWith("ROLE_")) {
                    return auth.substring(5); // Remove "ROLE_" prefix
                }
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to extract user type from authentication", e);
            return null;
        }
    }

    /**
     * Check if the authenticated user is a reseller
     *
     * @param authentication Spring Security Authentication object
     * @return true if user is a reseller
     */
    public static boolean isReseller(Authentication authentication) {
        String userType = extractUserType(authentication);
        return "RESELLER".equalsIgnoreCase(userType);
    }

    /**
     * Check if the authenticated user is an admin
     *
     * @param authentication Spring Security Authentication object
     * @return true if user is an admin
     */
    public static boolean isAdmin(Authentication authentication) {
        String userType = extractUserType(authentication);
        return "ADMIN".equalsIgnoreCase(userType);
    }
}
