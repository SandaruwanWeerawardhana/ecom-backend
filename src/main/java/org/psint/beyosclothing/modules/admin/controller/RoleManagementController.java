package org.psint.beyosclothing.modules.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.admin.dto.request.RoleCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.RoleUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.PermissionGroupDTO;
import org.psint.beyosclothing.modules.admin.dto.response.RoleDetailsResponseDTO;
import org.psint.beyosclothing.modules.admin.service.RoleManagementService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role Management Controller
 * Endpoints for managing admin roles and permissions
 * All endpoints require specific permissions
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/admin/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    /**
     * Create a new admin role
     * Required permission: CREATE_ROLE
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_ROLE')")
    public ResponseEntity<APIResponse<RoleDetailsResponseDTO>> createRole(
            @Valid @RequestBody RoleCreationRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Role creation request received from: {}", userDetails.getUsername());

        try {
            RoleDetailsResponseDTO response = roleManagementService.createRole(request, userDetails.getUsername());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.CREATED.getCode())
                            .success(true)
                            .message("Role created successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while creating role: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Resource not found while creating role: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error creating role: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to create role")
                            .build());
        }
    }

    /**
     * Get role by ID
     * Required permission: VIEW_ROLE
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_ROLE')")
    public ResponseEntity<APIResponse<RoleDetailsResponseDTO>> getRoleById(@PathVariable Long id) {
        log.info("Fetching role by ID: {}", id);

        try {
            RoleDetailsResponseDTO response = roleManagementService.getRoleById(id);

            return ResponseEntity
                    .ok(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Role retrieved successfully")
                            .data(response)
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Role not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error fetching role: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch role")
                            .build());
        }
    }

    /**
     * Get role by code
     * Required permission: VIEW_ROLE
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('VIEW_ROLE')")
    public ResponseEntity<APIResponse<RoleDetailsResponseDTO>> getRoleByCode(@PathVariable String code) {
        log.info("Fetching role by code: {}", code);

        try {
            RoleDetailsResponseDTO response = roleManagementService.getRoleByCode(code);

            return ResponseEntity
                    .ok(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Role retrieved successfully")
                            .data(response)
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Role not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error fetching role: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch role")
                            .build());
        }
    }

    /**
     * Get all admin roles with pagination
     * Required permission: VIEW_ROLE
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ROLE')")
    public ResponseEntity<APIResponse<PageResponse<RoleDetailsResponseDTO>>> getAllRoles(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION) String sortDir) {

        log.info("Fetching all roles - page: {}, size: {}", page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();

            Pageable pageable = PageRequest.of(page, size, sort);
            PageResponse<RoleDetailsResponseDTO> response = roleManagementService.getAllRoles(pageable);

            return ResponseEntity
                    .ok(APIResponse.<PageResponse<RoleDetailsResponseDTO>>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Roles retrieved successfully")
                            .data(response)
                            .build());

        } catch (Exception e) {
            log.error("Error fetching roles: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<PageResponse<RoleDetailsResponseDTO>>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch roles")
                            .build());
        }
    }

    /**
     * Update role permissions
     * Required permission: ASSIGN_PERMISSION
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ASSIGN_PERMISSION')")
    public ResponseEntity<APIResponse<RoleDetailsResponseDTO>> updateRolePermissions(
            @PathVariable Long id,
            @RequestBody List<String> permissionCodes,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Updating permissions for role ID: {} from: {}", id, userDetails.getUsername());

        try {
            RoleDetailsResponseDTO response = roleManagementService.updateRolePermissions(
                    id, permissionCodes, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Role permissions updated successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while updating permissions: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Role not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error updating role permissions: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to update role permissions")
                            .build());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSIGN_PERMISSION')")
    public ResponseEntity<APIResponse<RoleDetailsResponseDTO>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Updating permissions for role ID: {} from: {}", id, userDetails.getUsername());

        try {
            RoleDetailsResponseDTO response = roleManagementService.updateRole(
                    id, request, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Role permissions updated successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while updating permissions: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Role not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error updating role permissions: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<RoleDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to update role permissions")
                            .build());
        }
    }

    /**
     * Delete role
     * Required permission: DELETE_ROLE
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_ROLE')")
    public ResponseEntity<APIResponse<Void>> deleteRole(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Role deletion request for ID: {} from: {}", id, userDetails.getUsername());

        try {
            roleManagementService.deleteRole(id, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.DELETED.getCode())
                            .success(true)
                            .message("Role deleted successfully")
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while deleting role: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn("Role not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error deleting role: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to delete role")
                            .build());
        }
    }

    /**
     * Get all available permissions
     * Required permission: VIEW_ROLE (to see what permissions can be assigned)
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('VIEW_ROLE')")
    public ResponseEntity<APIResponse<List<RoleDetailsResponseDTO.PermissionDTO>>> getAllPermissions() {
        log.info("Fetching all available permissions");

        try {
            List<RoleDetailsResponseDTO.PermissionDTO> response = roleManagementService.getAllPermissions();

            return ResponseEntity
                    .ok(APIResponse.<List<RoleDetailsResponseDTO.PermissionDTO>>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Permissions retrieved successfully")
                            .data(response)
                            .build());

        } catch (Exception e) {
            log.error("Error fetching permissions: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<List<RoleDetailsResponseDTO.PermissionDTO>>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch permissions")
                            .build());
        }
    }

    /**
     * Get all available permissions grouped by module with hierarchy
     * RECOMMENDED: Use this endpoint for frontend - provides better structure
     * Required permission: VIEW_ROLE
     */
    @GetMapping("/permissions/grouped")
    @PreAuthorize("hasAuthority('VIEW_ROLE')")
    public ResponseEntity<APIResponse<List<PermissionGroupDTO>>> getAllPermissionsGrouped() {
        log.info("Fetching all available permissions grouped by module");

        try {
            List<PermissionGroupDTO> response = roleManagementService.getAllPermissionsGrouped();

            return ResponseEntity
                    .ok(APIResponse.<List<PermissionGroupDTO>>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Grouped permissions retrieved successfully")
                            .data(response)
                            .build());

        } catch (Exception e) {
            log.error("Error fetching grouped permissions: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<List<PermissionGroupDTO>>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch grouped permissions")
                            .build());
        }
    }
}
