package org.psint.beyosclothing.modules.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.common.service.RoleService;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.admin.dto.request.RoleCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.RoleUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.PermissionGroupDTO;
import org.psint.beyosclothing.modules.admin.dto.response.RoleDetailsResponseDTO;
import org.psint.beyosclothing.modules.admin.events.AssignPermissionsToRoleEvent;
import org.psint.beyosclothing.modules.admin.events.CreateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.service.AdminEventPublisherService;
import org.psint.beyosclothing.modules.auth.entity.RolePermission;
import org.psint.beyosclothing.modules.auth.entity.UserPermission;
import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.psint.beyosclothing.modules.auth.repository.RolePermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserPermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Role Service Implementation
 * ✅ MICROSERVICE-READY: Implements common interface, manages roles and permissions in Auth DB
 * This service is used by Admin module for role management operations
 * In microservice architecture, this would be exposed as REST endpoints
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleInternalServiceImpl implements RoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AdminEventPublisherService eventPublisher;

    @Override
    public RoleDetailsResponseDTO createRole(RoleCreationRequestDTO request, String createdBy) {
        log.info("Creating admin role with code: {}", request.getRoleCode());

        try {

            // Validate all permissions exist
            validatePermissions(request.getPermissionCodes());

            // Publish event to create role in Auth DB
            CreateAdminRoleEvent event = CreateAdminRoleEvent.builder()
                    .roleCode(request.getRoleCode())
                    .roleName(request.getRoleName())
                    .description(request.getDescription())
                    .userType("ADMIN")
                    .permissionCodes(request.getPermissionCodes())
                    .createdAt(LocalDateTime.now())
                    .createdBy(createdBy)
                    .build();

            eventPublisher.publishCreateAdminRoleEvent(event);

            log.info("Role creation event published for: {}", request.getRoleCode());

            // Wait a moment for event processing
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Fetch and return created role
            return getRoleByCode(request.getRoleCode());

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create role or Role with code already exists " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "authTransactionManager")
    public RoleDetailsResponseDTO getRoleById(Long roleId) {
        log.info("Fetching role by ID: {}", roleId);

        UserRole role = userRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with ID: " + roleId));

        return mapToResponseDTO(role);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "authTransactionManager")
    public RoleDetailsResponseDTO getRoleByCode(String roleCode) {
        log.info("Fetching role by code: {}", roleCode);

        UserRole role = userRoleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with code: " + roleCode));

        return mapToResponseDTO(role);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "authTransactionManager")
    public PageResponse<RoleDetailsResponseDTO> getAllRoles(Pageable pageable) {
        log.info("Fetching all admin roles with pagination");

        Page<UserRole> rolePage = userRoleRepository.findAll(pageable);

        // Filter only admin roles
        List<RoleDetailsResponseDTO> content = rolePage.getContent().stream()
                .filter(role -> "ADMIN".equals(role.getUserType()))
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());

        return PageResponse.<RoleDetailsResponseDTO>builder()
                .content(content)
                .pageNumber(rolePage.getNumber())
                .pageSize(rolePage.getSize())
                .totalElements((long) content.size())
                .totalPages(rolePage.getTotalPages())
                .last(rolePage.isLast())
                .first(rolePage.isFirst())
                .empty(content.isEmpty())
                .build();
    }

    @Override
    public RoleDetailsResponseDTO updateRolePermissions(Long roleId, List<String> permissionCodes, String updatedBy) {
        log.info("Updating permissions for role ID: {}", roleId);

        try {
            // Validate role exists
            UserRole role = userRoleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with ID: " + roleId));

            // Validate all permissions exist
            validatePermissions(permissionCodes);

            // Publish event to update permissions in Auth DB
            AssignPermissionsToRoleEvent event = AssignPermissionsToRoleEvent.builder()
                    .roleId(roleId)
                    .roleCode(role.getRoleCode())
                    .permissionCodes(permissionCodes)
                    .assignedBy(updatedBy)
                    .build();

            eventPublisher.publishAssignPermissionsEvent(event);

            log.info("Permission update event published for role: {}", role.getRoleCode());

            // Wait for event processing
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return getRoleById(roleId);

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating role permissions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update role permissions: " + e.getMessage());
        }
    }

    @Override
    @Transactional(transactionManager = "authTransactionManager")
    public RoleDetailsResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request, String updatedBy) {
        log.info("Updating role with ID: {}", roleId);

        try {
            // Validate role exists
            UserRole role = userRoleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with ID: " + roleId));

            // Check if role is a default role
//            if (isDefaultRole(role.getRoleCode())) {
//                throw new BadRequestException("Cannot update default role: " + role.getRoleCode());
//            }

            // Update role fields if provided
            if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
                role.setRoleCode(request.getRoleCode());
            }

            if (request.getRoleName() != null && !request.getRoleName().isBlank()) {
                role.setRoleName(request.getRoleName());
            }

            if (request.getDescription() != null) {
                role.setDescription(request.getDescription());
            }

            // Save role updates
            userRoleRepository.save(role);
            log.info("Role details updated successfully for role ID: {}", roleId);

            // Update permissions if provided (null means "no change")
            if (request.getPermissionCodes() != null) {
                List<String> permissionCodes = new ArrayList<>(new LinkedHashSet<>(request.getPermissionCodes()));
                log.info("Updating permissions for role ID: {}", roleId);

                // Delete existing permissions
                List<RolePermission> existingPermissions = rolePermissionRepository.findByRoleId(roleId);
                if (!existingPermissions.isEmpty()) {
                    rolePermissionRepository.deleteAll(existingPermissions);
                    rolePermissionRepository.flush();
                }
                log.info("Deleted {} existing permissions for role ID: {}", existingPermissions.size(), roleId);

                if (!permissionCodes.isEmpty()) {
                    // Validate all permissions exist
                    validatePermissions(permissionCodes);

                    int assignedCount = 0;
                    for (String code : permissionCodes) {
                        UserPermission permission = userPermissionRepository.findByPermissionCode(code)
                                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + code));

                        RolePermission rolePermission = RolePermission.builder()
                                .userRole(role)
                                .permission(permission)
                                .build();
                        rolePermissionRepository.save(rolePermission);
                        assignedCount++;
                    }
                    rolePermissionRepository.flush();
                    log.info("Assigned {}/{} permissions to role ID: {}", assignedCount, permissionCodes.size(), roleId);
                } else {
                    log.info("Cleared all permissions for role ID: {}", roleId);
                }
            }

            return getRoleById(roleId);

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update role: " + e.getMessage());
        }
    }

    @Override
    @Transactional(transactionManager = "authTransactionManager")
    public void deleteRole(Long roleId, String deletedBy) {
        log.info("Deleting role with ID: {}", roleId);

        try {
            UserRole role = userRoleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with ID: " + roleId));

            // Check if role is a default role
            if (isDefaultRole(role.getRoleCode())) {
                throw new BadRequestException("Cannot delete default role: " + role.getRoleCode());
            }

            userRoleRepository.delete(role);

            log.info("Role deleted successfully: {}", role.getRoleCode());

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete role: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "authTransactionManager")
    public List<RoleDetailsResponseDTO.PermissionDTO> getAllPermissions() {
        log.info("Fetching all available permissions");

        List<UserPermission> permissions = userPermissionRepository.findAll();

        return permissions.stream()
                .map(permission -> RoleDetailsResponseDTO.PermissionDTO.builder()
                        .id(permission.getId())
                        .permissionCode(permission.getPermissionCode())
                        .permissionName(permission.getPermissionName())
                        .module(permission.getModule())
                        .isParent(permission.getIsParent())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "authTransactionManager")
    public List<PermissionGroupDTO> getAllPermissionsGrouped() {
        log.info("Fetching all permissions grouped by module with hierarchy");

        List<UserPermission> allPermissions = userPermissionRepository.findAll();

        Map<String, List<UserPermission>> permissionsByModule = allPermissions.stream()
                .collect(Collectors.groupingBy(UserPermission::getModule));

        return permissionsByModule.entrySet().stream()
                .map(entry -> {
                    String module = entry.getKey();
                    List<UserPermission> modulePermissions = entry.getValue();

                    List<UserPermission> parentPermissions = modulePermissions.stream()
                            .filter(p -> p.getIsParent() != null && p.getIsParent())
                            .sorted(Comparator.comparing(UserPermission::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                            .collect(Collectors.toList());

                    List<PermissionGroupDTO.PermissionItemDTO> permissionItems = parentPermissions.stream()
                            .map(parent -> {
                                List<PermissionGroupDTO.PermissionItemDTO> children = modulePermissions.stream()
                                        .filter(p -> p.getIsParent() != null && !p.getIsParent()
                                                && p.getParent() != null
                                                && p.getParent().getId().equals(parent.getId()))
                                        .sorted(Comparator.comparing(UserPermission::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                                        .map(child -> PermissionGroupDTO.PermissionItemDTO.builder()
                                                .id(child.getId())
                                                .permissionCode(child.getPermissionCode())
                                                .permissionName(child.getPermissionName())
                                                .description(child.getDescription())
                                                .isParent(false)
                                                .displayOrder(child.getDisplayOrder())
                                                .children(null)
                                                .build())
                                        .collect(Collectors.toList());

                                return PermissionGroupDTO.PermissionItemDTO.builder()
                                        .id(parent.getId())
                                        .permissionCode(parent.getPermissionCode())
                                        .permissionName(parent.getPermissionName())
                                        .description(parent.getDescription())
                                        .isParent(true)
                                        .displayOrder(parent.getDisplayOrder())
                                        .children(children)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    Integer moduleOrder = parentPermissions.isEmpty() ? 999 :
                            parentPermissions.get(0).getDisplayOrder();

                    return PermissionGroupDTO.builder()
                            .module(module)
                            .moduleDisplayName(getModuleDisplayName(module))
                            .displayOrder(moduleOrder)
                            .permissions(permissionItems)
                            .build();
                })
                .sorted(Comparator.comparing(PermissionGroupDTO::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private String getModuleDisplayName(String module) {
        switch (module) {
            case "ADMIN":
                return "Admin Management";
            case "CUSTOMER":
                return "Customer Management";
            case "PRODUCT":
                return "Product Management";
            case "ORDER":
                return "Order Management";
            case "INVENTORY":
                return "Inventory Management";
            case "REPORTING":
                return "Reports & Analytics";
            default:
                return module;
        }
    }

    private void validatePermissions(List<String> permissionCodes) {
        for (String code : permissionCodes) {
            if (!userPermissionRepository.existsByPermissionCode(code)) {
                throw new BadRequestException("Permission not found: " + code);
            }
        }
    }

    private boolean isDefaultRole(String roleCode) {
        return List.of("SUPER_ADMIN", "MANAGER", "CASHIER", "INVENTORY_OFFICER")
                .contains(roleCode);
    }

    private RoleDetailsResponseDTO mapToResponseDTO(UserRole role) {
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(role.getId());

        List<RoleDetailsResponseDTO.PermissionDTO> permissions = rolePermissions.stream()
                .map(rp -> RoleDetailsResponseDTO.PermissionDTO.builder()
                        .id(rp.getPermission().getId())
                        .permissionCode(rp.getPermission().getPermissionCode())
                        .permissionName(rp.getPermission().getPermissionName())
                        .module(rp.getPermission().getModule())
                        .isParent(rp.getPermission().getIsParent())
                        .build())
                .collect(Collectors.toList());

        return RoleDetailsResponseDTO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .userType(role.getUserType())
                .permissions(permissions)
                .totalPermissions(permissions.size())
                .isActive(role.getIsActive())
                .createdAt(role.getDateCreated())
                .updatedAt(role.getDateUpdated())
                .build();
    }
}

