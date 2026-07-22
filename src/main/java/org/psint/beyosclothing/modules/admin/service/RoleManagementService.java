package org.psint.beyosclothing.modules.admin.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.admin.dto.request.RoleCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.RoleUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.PermissionGroupDTO;
import org.psint.beyosclothing.modules.admin.dto.response.RoleDetailsResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Role Management Service Interface
 * Business logic for admin role management
 */
public interface RoleManagementService {

    /**
     * Create a new admin role
     * @param request Role creation request
     * @param createdBy Who created the role
     * @return Created role details
     */
    RoleDetailsResponseDTO createRole(RoleCreationRequestDTO request, String createdBy);

    /**
     * Get role by ID
     * @param roleId Role ID
     * @return Role details with permissions
     */
    RoleDetailsResponseDTO getRoleById(Long roleId);

    /**
     * Get role by code
     * @param roleCode Role code
     * @return Role details with permissions
     */
    RoleDetailsResponseDTO getRoleByCode(String roleCode);

    /**
     * Get all admin roles
     * @param pageable Pagination parameters
     * @return Paginated role list
     */
    PageResponse<RoleDetailsResponseDTO> getAllRoles(Pageable pageable);

    /**
     * Update role permissions
     * @param roleId Role ID
     * @param permissionCodes New permission codes
     * @param updatedBy Who updated
     * @return Updated role details
     */
    RoleDetailsResponseDTO updateRolePermissions(Long roleId, List<String> permissionCodes, String updatedBy);

    RoleDetailsResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request , String updatedBy);
    /**
     * Delete role (if not assigned to any admin)
     * @param roleId Role ID
     * @param deletedBy Who deleted
     */
    void deleteRole(Long roleId, String deletedBy);

    /**
     * Get all available permissions
     * @return List of all permissions
     */
    List<RoleDetailsResponseDTO.PermissionDTO> getAllPermissions();

    /**
     * Get all permissions grouped by module with hierarchy
     * Better for frontend rendering with parent-child structure
     * @return List of permission groups categorized by module
     */
    List<PermissionGroupDTO> getAllPermissionsGrouped();
}