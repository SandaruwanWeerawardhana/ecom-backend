package org.psint.beyosclothing.modules.auth.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RPC listener for password verification and update requests from other services.
 * Handles password verification and update by userId in the auth DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordRpcListener {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verify the current password for a given user by userId.
     * Expected request payload:
     *   { "userId": <long>, "currentPassword": "plain-text" }
     * Response:
     *   { "success": true/false }
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue.verify-password}")
    @Transactional("authTransactionManager")
    public Map<String, Object> handleVerifyPassword(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (request == null) {
                log.warn("handleVerifyPassword called with null request");
                response.put("success", false);
                return response;
            }

            Object userIdObj = request.get("userId");
            String currentPassword = (String) request.get("currentPassword");

            if (userIdObj == null || currentPassword == null) {
                log.warn("Missing userId or currentPassword in verify request");
                response.put("success", false);
                return response;
            }

            Long userId = null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                try {
                    userId = Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid userId format: {}", userIdObj);
                    response.put("success", false);
                    return response;
                }
            }

            if (userId == null) {
                log.warn("Invalid userId in verify request: {}", userIdObj);
                response.put("success", false);
                return response;
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found for userId={}", userId);
                response.put("success", false);
                return response;
            }

            User user = userOpt.get();
            String storedPassword = user.getPassword();
            boolean matches = storedPassword != null && passwordEncoder.matches(currentPassword, storedPassword);

            response.put("success", matches);
            log.info("Password verification result for userId={}: {}", userId, matches);
            return response;
        } catch (Exception e) {
            log.error("Error while verifying password via RPC", e);
            response.put("success", false);
            return response;
        }
    }

    /**
     * Update the password for a given user by userId.
     *
     * Expected request payload:
     *   { "userId": <long>, "newPassword": "already-encoded" }
     *
     * Response:
     *   { "success": true/false }
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue.update-password}")
    @Transactional("authTransactionManager")
    public Map<String, Object> handleUpdatePassword(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (request == null) {
                log.warn("handleUpdatePassword called with null request");
                response.put("success", false);
                return response;
            }

            Object userIdObj = request.get("userId");
            String newPassword = (String) request.get("newPassword"); // already encoded

            if (userIdObj == null || newPassword == null) {
                log.warn("Missing userId or newPassword in update request");
                response.put("success", false);
                return response;
            }

            Long userId = null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                try {
                    userId = Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid userId format: {}", userIdObj);
                    response.put("success", false);
                    return response;
                }
            }

            if (userId == null) {
                log.warn("Invalid userId in update request: {}", userIdObj);
                response.put("success", false);
                return response;
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found for userId={}", userId);
                response.put("success", false);
                return response;
            }

            User user = userOpt.get();
            user.setPassword(newPassword);
            userRepository.save(user);

            response.put("success", true);
            log.info("Password updated successfully for userId={}", userId);
            return response;
        } catch (Exception e) {
            log.error("Error while updating password via RPC", e);
            response.put("success", false);
            return response;
        }
    }
}

