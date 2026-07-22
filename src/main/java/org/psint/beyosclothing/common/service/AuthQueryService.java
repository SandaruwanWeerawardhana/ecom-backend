package org.psint.beyosclothing.common.service;

import org.psint.beyosclothing.modules.admin.dto.response.RoleInfoDTO;
import org.psint.beyosclothing.modules.admin.dto.response.UserInfoDTO;

import java.util.Optional;

/**
 * Auth Query Service Interface (API Contract)
 * This interface defines the contract between Admin and Auth modules
 * In microservice architecture:
 * - Auth Service will implement this interface
 * - Admin Service will call this via HTTP client (Feign/RestTemplate)
 * - This interface becomes the REST API contract
 */
public interface AuthQueryService {

    /**
     * Check if email exists in Auth DB
     */
    boolean emailExists(String email);

    /**
     * Get user information by email
     */
    Optional<UserInfoDTO> getUserByEmail(String email);

    /**
     * Get user information by ID
     */
    Optional<UserInfoDTO> getUserById(Long userId);

    /**
     * Get role information by role code
     */
    Optional<RoleInfoDTO> getRoleByCode(String roleCode);

    /**
     * Get role information by role ID
     */
    Optional<RoleInfoDTO> getRoleById(Long roleId);

    /**
     * Check if user has specific permission
     */
    boolean hasPermission(Long userId, String permissionCode);
}

