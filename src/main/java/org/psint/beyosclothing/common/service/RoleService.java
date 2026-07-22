package org.psint.beyosclothing.common.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.admin.dto.request.RoleCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.RoleUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.PermissionGroupDTO;
import org.psint.beyosclothing.modules.admin.dto.response.RoleDetailsResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Role Service Interface (API Contract)
 * This interface defines the contract between Admin and Auth modules
 * In microservice architecture:
 * - Auth Service will implement this interface
 * - Admin Service will call this via HTTP client (Feign/RestTemplate)
 * - This interface becomes the REST API contract
 */
public interface RoleService {

    /**
     * Create a new admin role
     */
    RoleDetailsResponseDTO createRole(RoleCreationRequestDTO request, String createdBy);

    /**
     * Get role by ID
     */
    RoleDetailsResponseDTO getRoleById(Long roleId);

    /**
     * Get role by code
     */
    RoleDetailsResponseDTO getRoleByCode(String roleCode);

    /**
     * Get all admin roles with pagination
     */
    PageResponse<RoleDetailsResponseDTO> getAllRoles(Pageable pageable);

    /**
     * Update role permissions
     */
    RoleDetailsResponseDTO updateRolePermissions(Long roleId, List<String> permissionCodes, String updatedBy);

    /**
     * Update role details (code, name, description) and permissions
     */
    RoleDetailsResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request, String updatedBy);

    /**
     * Delete a role
     */
    void deleteRole(Long roleId, String deletedBy);

    /**
     * Get all available permissions
     */
    List<RoleDetailsResponseDTO.PermissionDTO> getAllPermissions();

    /**
     * Get all permissions grouped by module with hierarchy
     */
    List<PermissionGroupDTO> getAllPermissionsGrouped();
}

