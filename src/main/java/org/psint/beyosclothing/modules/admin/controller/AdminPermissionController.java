package org.psint.beyosclothing.modules.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.admin.events.InitializePermissionsEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin Permission Management Controller
 * Handles permission initialization and management
 */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@Slf4j
public class AdminPermissionController {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.admin}")
    private String adminExchange;

    @Value("${app.rabbitmq.routing-key.admin-permissions-initialize}")
    private String permissionsInitializeRoutingKey;

    /**
     * Initialize hierarchical permissions
     * POST /api/admin/permissions/initialize
     */
    @PostMapping("/initialize")
    public ResponseEntity<APIResponse<String>> initializePermissions() {
        log.info("🔧 Triggering permission initialization...");

        try {
            // Create and publish event
            InitializePermissionsEvent event = InitializePermissionsEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, permissionsInitializeRoutingKey, event);

            log.info("✅ Permission initialization event published successfully");

            return ResponseEntity.ok(APIResponse.success(
                    "Permission initialization triggered successfully. Check logs for details.",
                    event.getRequestId()
            ));

        } catch (Exception e) {
            log.error("❌ Error triggering permission initialization: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(APIResponse.error("Failed to trigger permission initialization: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     * GET /api/admin/permissions/health
     */
    @GetMapping("/health")
    public ResponseEntity<APIResponse<String>> health() {
        return ResponseEntity.ok(APIResponse.success("Admin Permission Controller is running", "OK"));
    }
}
