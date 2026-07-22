package org.psint.beyosclothing.modules.admin.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.entity.AdminEntity;
import org.psint.beyosclothing.modules.admin.repository.AdminRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin Details Lookup Consumer
 * Handles RabbitMQ RPC requests for admin details lookup with userId matching.
 * Uses Map-based messaging to avoid cross-module DTO dependencies.
 *
 * Request keys:  requestId, userId
 * Response keys: requestId, found, adminId, uuid, userId (verification),
 *                firstName, lastName, fullName, email, phone, phoneNumber, isActive
 *
 * ✅ VERIFICATION: Returns both userId and adminId so auth module can confirm match
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminDetailsLookupConsumer {

    private final AdminRepository adminRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.admin-details-lookup-request:admin.details.lookup.request}")
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public Map<String, Object> handleAdminDetailsLookup(Map<String, Object> request) {

        String requestId = request != null ? (String) request.get("requestId") : null;
        log.debug("🔍 [ADMIN LOOKUP] Received request: requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            Long userId = null;
            Object userIdObj = request != null ? request.get("userId") : null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            }

            if (userId == null) {
                log.warn("[ADMIN LOOKUP] Missing userId in request: {}", requestId);
                response.put("found", false);
                response.put("error", "userId is required");
                return response;
            }

            AdminEntity admin = adminRepository.findByUserId(userId).orElse(null);

            if (admin == null) {
                log.warn("[ADMIN LOOKUP] Admin not found for userId={}", userId);
                response.put("found", false);
                return response;
            }

            response.put("found", true);
            response.put("adminId", admin.getId());
            response.put("uuid", admin.getUuid());
            response.put("userId", admin.getUserId());
            response.put("firstName", admin.getFirstName());
            response.put("lastName", admin.getLastName());
            response.put("fullName", admin.getFullName());
            response.put("email", admin.getEmail());
            response.put("phone", admin.getPhone());
            // New normalized key expected by AuthServiceImpl.getCurrentUser()
            response.put("phoneNumber", admin.getPhone());
            response.put("isActive", admin.getIsActive());

            log.info("✅ [ADMIN LOOKUP] Found admin matched: userId={} → adminId={}, uuid={}, email={}",
                userId, admin.getId(), admin.getUuid(), admin.getEmail());

        } catch (Exception e) {
            log.error("❌ [ADMIN LOOKUP] Error processing request requestId={}", requestId, e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
        }

        return response;
    }
}

