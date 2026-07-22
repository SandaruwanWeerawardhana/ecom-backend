package org.psint.beyosclothing.modules.pos.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.entity.PosCashierEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCashierRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * POS Cashier Lookup Consumer
 * Handles RabbitMQ RPC requests to check whether a user is a POS cashier.
 * Uses Map-based messaging to avoid cross-module DTO dependencies.
 *
 * Request keys:  requestId, userId (for userId lookup)
 *                requestId, adminId (for adminId lookup)
 * Response keys: requestId, found, isPosCashier, cashierUuid, cashierName, isActive
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PosCashierLookupConsumer {

    private final PosCashierRepository cashierRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-cashier-lookup-request:pos.cashier.lookup.request}")
    @Transactional(value = "posTransactionManager", readOnly = true)
    public Map<String, Object> handleCashierLookup(Map<String, Object> request) {

        String requestId = request != null ? (String) request.get("requestId") : null;
        log.debug("🔍 [POS CASHIER LOOKUP] Received request: requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            Long userId = null;
            Object userIdObj = request != null ? request.get("userId") : null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            }

            if (userId == null) {
                log.warn("[POS CASHIER LOOKUP] Missing userId in request: {}", requestId);
                response.put("found", false);
                response.put("isPosCashier", false);
                response.put("error", "userId is required");
                return response;
            }

            PosCashierEntity cashier = cashierRepository.findByUserIdAndIsActiveTrue(userId).orElse(null);

            if (cashier == null) {
                log.debug("[POS CASHIER LOOKUP] No active cashier for userId={}", userId);
                response.put("found", false);
                response.put("isPosCashier", false);
                return response;
            }

            response.put("found", true);
            response.put("isPosCashier", true);
            response.put("cashierUuid", cashier.getUuid());
            response.put("cashierName", cashier.getName());
            response.put("isActive", cashier.getIsActive());

            log.info("✅ [POS CASHIER LOOKUP] userId={} is a POS cashier → uuid={}", userId, cashier.getUuid());

        } catch (Exception e) {
            log.error("❌ [POS CASHIER LOOKUP] Error processing request requestId={}", requestId, e);
            response.put("found", false);
            response.put("isPosCashier", false);
            response.put("error", "Internal error: " + e.getMessage());
        }

        return response;
    }

    /**
     * Handle cashier lookup by admin ID (tableId)
     * Looks up active cashier in pos_cashiers table where user_id matches adminId
     * Used by Auth module during getCurrentUser() for admin users
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.pos-cashier-by-admin-lookup-request:pos.cashier.by.admin.lookup.request}")
    @Transactional(value = "posTransactionManager", readOnly = true)
    public Map<String, Object> handleCashierLookupByAdminId(Map<String, Object> request) {

        String requestId = request != null ? (String) request.get("requestId") : null;
        log.debug("[POS CASHIER LOOKUP BY ADMIN] Received request: requestId={}", requestId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            Long adminId = null;
            Object adminIdObj = request != null ? request.get("adminId") : null;
            if (adminIdObj instanceof Number) {
                adminId = ((Number) adminIdObj).longValue();
            }

            if (adminId == null) {
                log.warn("[POS CASHIER LOOKUP BY ADMIN] Missing adminId in request: {}", requestId);
                response.put("found", false);
                response.put("error", "adminId is required");
                return response;
            }

            // Look up active cashier by user_id (which matches admin.id)
            PosCashierEntity cashier = cashierRepository.findByUserIdAndIsActiveTrue(adminId).orElse(null);

            if (cashier == null) {
                log.debug("[POS CASHIER LOOKUP BY ADMIN] No active cashier for adminId={}", adminId);
                response.put("found", false);
                return response;
            }

            response.put("found", true);
            response.put("cashierUuid", cashier.getUuid());
            response.put("cashierName", cashier.getName());
            response.put("userId", cashier.getUserId());
            response.put("isActive", cashier.getIsActive());

            log.info("[POS CASHIER LOOKUP BY ADMIN] adminId={} is a POS cashier → uuid={}", adminId, cashier.getUuid());

        } catch (Exception e) {
            log.error("[POS CASHIER LOOKUP BY ADMIN] Error processing request requestId={}", requestId, e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
        }

        return response;
    }
}

