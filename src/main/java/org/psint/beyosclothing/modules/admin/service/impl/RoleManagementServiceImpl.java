package org.psint.beyosclothing.modules.admin.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.common.service.RoleService;
import org.psint.beyosclothing.modules.admin.dto.request.RoleCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.RoleUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.PermissionGroupDTO;
import org.psint.beyosclothing.modules.admin.dto.response.RoleDetailsResponseDTO;
import org.psint.beyosclothing.modules.admin.service.RoleManagementService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Role Management Service Implementation
 * ✅ MICROSERVICE-READY: Uses common RoleService interface (API Contract)
 * This service acts as a facade that delegates to the Auth module's role service
 * In microservice architecture, this would make HTTP calls to Auth service API via Feign client
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleManagementServiceImpl implements RoleManagementService {

    private final RoleService roleService; // ✅ Common interface, not auth module specific

    @Override
    public RoleDetailsResponseDTO createRole(RoleCreationRequestDTO request, String createdBy) {
        return roleService.createRole(request, createdBy);
    }

    @Override
    public RoleDetailsResponseDTO getRoleById(Long roleId) {
        return roleService.getRoleById(roleId);
    }

    @Override
    public RoleDetailsResponseDTO getRoleByCode(String roleCode) {
        return roleService.getRoleByCode(roleCode);
    }

    @Override
    public PageResponse<RoleDetailsResponseDTO> getAllRoles(Pageable pageable) {
        return roleService.getAllRoles(pageable);
    }

    @Override
    public RoleDetailsResponseDTO updateRolePermissions(Long roleId, List<String> permissionCodes, String updatedBy) {
        return roleService.updateRolePermissions(roleId, permissionCodes, updatedBy);
    }

    @Override
    public RoleDetailsResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request, String updatedBy) {
        return roleService.updateRole(roleId, request, updatedBy);
    }

    @Override
    public void deleteRole(Long roleId, String deletedBy) {
        roleService.deleteRole(roleId, deletedBy);
    }

    @Override
    public List<RoleDetailsResponseDTO.PermissionDTO> getAllPermissions() {
        return roleService.getAllPermissions();
    }

    @Override
    public List<PermissionGroupDTO> getAllPermissionsGrouped() {
        return roleService.getAllPermissionsGrouped();
    }
}
