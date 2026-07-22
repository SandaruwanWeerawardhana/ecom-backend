package org.psint.beyosclothing.modules.auth.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.events.AdminRolePermissionEvent;
import org.psint.beyosclothing.modules.auth.entity.RolePermission;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.psint.beyosclothing.modules.auth.repository.RolePermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRoleRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auth-side RPC consumer for Admin Role + Permission Lookup.
 * Queue  : admin.role.permission.lookup.request.queue
 * Routing: beyos.exchange.admin → admin.role.permission.lookup.request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminRolePermissionLookupConsumer {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.admin-role-permission-lookup-request:admin.role.permission.lookup.request.queue}")
    @Transactional(value = "authTransactionManager", readOnly = true)
    public Map<String, Object> handleRolePermissionLookup(AdminRolePermissionEvent request) {

        String requestId = request != null ? request.getRequestId() : null;
        log.info("🔍 [ROLE-PERM LOOKUP] requestId={}, roleCodes={}",
                requestId, request != null ? request.getRoleCodes() : null);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            if (request == null || request.getRoleCodes() == null || request.getRoleCodes().isEmpty()) {
                log.warn("[ROLE-PERM LOOKUP] No roleCodes supplied");
                response.put("roles", List.of());
                return response;
            }

            List<Map<String, Object>> roleResults = new ArrayList<>();

            for (String roleCode : request.getRoleCodes()) {

                // 1. Find the role in user_role table
                UserRole role = userRoleRepository.findByRoleCode(roleCode).orElse(null);
                if (role == null) {
                    log.warn("[ROLE-PERM LOOKUP] Role not found in user_role table: {}", roleCode);
                    continue;
                }

                // 2. Fetch permissions from role_permission table (no isActive filter on join row —
                //    we check permission.isActive so only active permission entities are included)
                List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(role.getId());
                List<Map<String, Object>> permissionList = new ArrayList<>();
                for (RolePermission rp : rolePermissions) {
                    // Skip permissions that are soft-deleted
                    if (Boolean.FALSE.equals(rp.getPermission().getIsActive())) {
                        continue;
                    }
                    Map<String, Object> perm = new HashMap<>();
                    perm.put("permissionId", rp.getPermission().getId());
                    perm.put("permissionCode", rp.getPermission().getPermissionCode());
                    perm.put("permissionName", rp.getPermission().getPermissionName());
                    perm.put("description", rp.getPermission().getDescription());
                    perm.put("module", rp.getPermission().getModule());
                    permissionList.add(perm);
                }

                // 3. Fetch users assigned to this role from user table
                List<User> users = userRepository.findByUserRoleId(role.getId());
                List<Long> userIds = users.stream().map(User::getId).toList();

                // 4. Always include the role even if permissions list is empty,
                //    so the admin details are still returned
                Map<String, Object> roleMap = new HashMap<>();
                roleMap.put("roleId", role.getId());
                roleMap.put("roleCode", role.getRoleCode());
                roleMap.put("roleName", role.getRoleName());
                roleMap.put("description", role.getDescription());
                roleMap.put("userType", role.getUserType());
                roleMap.put("userIds", userIds);
                roleMap.put("permissions", permissionList);

                roleResults.add(roleMap);
                log.info("✅ [ROLE-PERM LOOKUP] role={} → {} permissions, {} users",
                        roleCode, permissionList.size(), userIds.size());
            }

            response.put("roles", roleResults);

        } catch (Exception e) {
            log.error("❌ [ROLE-PERM LOOKUP] Error requestId={}: {}", requestId, e.getMessage(), e);
            response.put("roles", List.of());
            response.put("error", e.getMessage());
        }

        return response;
    }
}

