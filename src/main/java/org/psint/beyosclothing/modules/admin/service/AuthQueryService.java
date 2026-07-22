package org.psint.beyosclothing.modules.admin.service;

import org.psint.beyosclothing.modules.admin.dto.response.RoleInfoDTO;
import org.psint.beyosclothing.modules.admin.dto.response.UserInfoDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Auth Query Service Interface
 * Provides read-only access to Auth module data via event-based communication
 * This prevents direct database access from Admin module to Auth module
 */
public interface AuthQueryService {

    boolean emailExists(String email);

    Optional<UserInfoDTO> getUserByEmail(String email);

    Optional<UserInfoDTO> getUserById(Long userId);

    Optional<RoleInfoDTO> getRoleByCode(String roleCode);

    Optional<RoleInfoDTO> getRoleById(Long roleId);

    boolean hasPermission(Long userId, String permissionCode);

    /**
     * Query Auth DB via RabbitMQ RPC:
     * For each roleCode in the list, return the role info + userIds.
     *
     * @param roleCodes e.g. ["SUPER_ADMIN", "CASHIER"]
     * @return map keyed by roleCode → { role: RoleInfoDTO, userIds: List<Long> }
     */
    Map<String, RolePermissionResult> getRolesWithPermissions(List<String> roleCodes);

    /**
     * Container for role + assigned userIds.
     */
    record RolePermissionResult(
            RoleInfoDTO role,
            List<Long> userIds
    ) {}
}
