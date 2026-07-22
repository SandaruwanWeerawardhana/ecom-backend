package org.psint.beyosclothing.modules.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.dto.response.RoleInfoDTO;
import org.psint.beyosclothing.modules.admin.dto.response.UserInfoDTO;
import org.psint.beyosclothing.modules.admin.events.AdminRolePermissionEvent;
import org.psint.beyosclothing.modules.admin.service.AuthQueryService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Auth Query Service Implementation (Admin Module)
 * Delegates basic queries to the common AuthQueryService (same-process, Auth DB).
 * getRolesWithPermissions uses RabbitMQ RPC: Admin exchange → Auth consumer.
 */
@Service("adminAuthQueryService")
@RequiredArgsConstructor
@Slf4j
public class AuthQueryServiceImpl implements AuthQueryService {

    /** Common in-process service backed by Auth DB repositories */
    private final org.psint.beyosclothing.common.service.AuthQueryService authQueryService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.rabbitmq.exchange.admin:beyos.exchange.admin}")
    private String adminExchange;

    /** routing key for the admin.role.permission.lookup.request queue */
    private static final String ROLE_PERM_ROUTING_KEY = "admin.role.permission.lookup.request";

    // ── existing delegating methods ──────────────────────────────────────

    @Override
    public boolean emailExists(String email) {
        return authQueryService.emailExists(email);
    }

    @Override
    public Optional<UserInfoDTO> getUserByEmail(String email) {
        return authQueryService.getUserByEmail(email);
    }

    @Override
    public Optional<UserInfoDTO> getUserById(Long userId) {
        return authQueryService.getUserById(userId);
    }

    @Override
    public Optional<RoleInfoDTO> getRoleByCode(String roleCode) {
        return authQueryService.getRoleByCode(roleCode);
    }

    @Override
    public Optional<RoleInfoDTO> getRoleById(Long roleId) {
        return authQueryService.getRoleById(roleId);
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        return authQueryService.hasPermission(userId, permissionCode);
    }

    // ── new: RabbitMQ RPC for role + permissions (returns role + userIds only) ─────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, RolePermissionResult> getRolesWithPermissions(List<String> roleCodes) {
        log.info("Fetching roles+permissions via RabbitMQ RPC for roleCodes={}", roleCodes);

        Map<String, RolePermissionResult> result = new LinkedHashMap<>();

        try {
            AdminRolePermissionEvent request = AdminRolePermissionEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .roleCodes(roleCodes)
                    .build();

            Object rawResponse = rabbitTemplate.convertSendAndReceive(
                    adminExchange, ROLE_PERM_ROUTING_KEY, request);

            if (rawResponse == null) {
                log.warn("No response from Auth module for role-permission lookup requestId={}",
                        request.getRequestId());
                return result;
            }

            String json = objectMapper.writeValueAsString(rawResponse);
            Map<String, Object> responseMap = objectMapper.readValue(json, Map.class);

            List<Map<String, Object>> roles =
                    (List<Map<String, Object>>) responseMap.getOrDefault("roles", List.of());

            for (Map<String, Object> roleMap : roles) {
                String roleCode = (String) roleMap.get("roleCode");

                RoleInfoDTO roleInfo = RoleInfoDTO.builder()
                        .roleId(((Number) roleMap.get("roleId")).longValue())
                        .roleCode(roleCode)
                        .roleName((String) roleMap.get("roleName"))
                        .description((String) roleMap.get("description"))
                        .userType((String) roleMap.get("userType"))
                        .build();

                List<Number> rawIds = (List<Number>) roleMap.getOrDefault("userIds", List.of());
                List<Long> userIds = rawIds.stream().map(Number::longValue).toList();

                result.put(roleCode, new RolePermissionResult(roleInfo, userIds));
                log.info("✅ role={} → {} userIds", roleCode, userIds.size());
            }

        } catch (Exception e) {
            log.error("Error in getRolesWithPermissions RPC: {}", e.getMessage(), e);
        }

        return result;
    }
}
