package org.psint.beyosclothing.modules.auth.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRoleRepository;
import org.psint.beyosclothing.modules.auth.service.UsernameGeneratorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * RPC Consumer for User Creation Requests
 * Handles synchronous user creation requests from other modules (e.g., Reseller)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreationRpcConsumer {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UsernameGeneratorService usernameGenerator;

    /**
     * Handle RPC user creation requests (with response)
     * This is different from the event-based consumer - it sends back a response
     */
    @RabbitListener(queues = "auth.user.create.rpc.queue")
    @Transactional("authTransactionManager")
    public Map<String, Object> handleUserCreationRequest(Map<String, Object> request) {
        log.info("Received RPC user creation request for email: {}", request.get("email"));

        Map<String, Object> response = new HashMap<>();

        try {
            String email = (String) request.get("email");
            String password = (String) request.get("password");
            String firstName = (String) request.get("firstName");
            String lastName = (String) request.get("lastName");
            String userType = (String) request.get("userType");
            String roleCode = (String) request.get("roleCode");
            Boolean emailVerified = (Boolean) request.getOrDefault("emailVerified", false);
            Boolean accountLocked = (Boolean) request.getOrDefault("accountLocked", false);

            // Validate required fields
            if (email == null || password == null || userType == null) {
                response.put("success", false);
                response.put("error", "Missing required fields");
                return response;
            }

            // Check if user already exists
            if (userRepository.existsByActiveEmail(email)) {
                response.put("success", false);
                response.put("error", "User with this email already exists");
                return response;
            }

            // Use provided roleCode if available, otherwise fall back to default role for userType
            UserRole role;
            if (roleCode != null && !roleCode.isBlank()) {
                log.info("Looking up role by provided roleCode: {}", roleCode);
                role = userRoleRepository.findByRoleCode(roleCode)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleCode));
            } else {
                role = findOrCreateDefaultRole(userType);
            }
            log.info("Assigned role {} to new user of type {}", role.getRoleCode(), userType);
            // Generate username
            String generatedUsername = usernameGenerator.generateUsername(userType);

            // Create user
            User user = User.builder()
                    .username(generatedUsername)
                    .email(email)
                    .password(password) // Already hashed
                    .userType(userType)
                    .userRoleId(role.getId())
                    .emailVerified(emailVerified)
                    .accountLocked(accountLocked)
                    .loginAttempts(0)
                    .build();

            user = userRepository.save(user);

            log.info("✅ User created successfully via RPC: {} (ID: {})", email, user.getId());

            // Send success response
            response.put("success", true);
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());

        } catch (Exception e) {
            log.error("❌ Error creating user via RPC: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    private UserRole findOrCreateDefaultRole(String userType) {
        log.info("Finding default role for user type: {}", userType);
        // Try to find default role for this user type
        String defaultRoleCode = switch (userType.toUpperCase()) {
            case "RESELLER" -> "RESELLER";
            case "CUSTOMER" -> "CUSTOMER";
            case "ADMIN" -> "ADMIN_STAFF";
            default -> "USER";
        };

        return userRoleRepository.findByRoleCode(defaultRoleCode)
                .orElseGet(() -> {
                    log.warn("Default role {} not found, creating basic role", defaultRoleCode);
                    UserRole newRole = UserRole.builder()
                            .roleCode(defaultRoleCode)
                            .roleName(userType + " Basic Role")
                            .description("Default role for " + userType)
                            .userType(userType)
                            .build();
                    return userRoleRepository.save(newRole);
                });
    }
}
