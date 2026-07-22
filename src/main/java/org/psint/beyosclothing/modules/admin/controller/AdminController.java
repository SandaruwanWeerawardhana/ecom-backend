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
import org.psint.beyosclothing.modules.admin.dto.request.AdminCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AdminUpdatePasswordDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AdminUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AssignRoleRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.AdminDetailsResponseDTO;
import org.psint.beyosclothing.modules.admin.service.AdminService;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Admin Management Controller
 * Endpoints for managing admin users
 * All endpoints require specific permissions
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;

    private static final String ADMIN_NOT_FOUND_LOG = "Admin not found: {}";

    /**
     * Create a new admin user
     * Required permission: CREATE_ADMIN
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CREATE_ADMIN')")
    public ResponseEntity<APIResponse<AdminDetailsResponseDTO>> createAdmin(
            @Valid @RequestBody AdminCreationRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Admin creation request received from: {}", userDetails.getUsername());
        log.info("Admin role: {}", request.getRoleCode());

        try {
            AdminDetailsResponseDTO response = adminService.createAdmin(request, userDetails.getUsername());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Admin created successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while creating admin: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn(ADMIN_NOT_FOUND_LOG, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error creating admin: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to create admin")
                            .build());
        }
    }

    /**
     * Assign role to admin user
     * Required permission: ASSIGN_ADMIN_ROLE
     */
    @PutMapping("/assign-role")
    @PreAuthorize("hasAuthority('ASSIGN_ADMIN_ROLE')")
    public ResponseEntity<APIResponse<AdminDetailsResponseDTO>> assignRole(
            @Valid @RequestBody AssignRoleRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Role assignment request received from: {}", userDetails.getUsername());

        try {
            AdminDetailsResponseDTO response = adminService.assignRoleToAdmin(request, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Role assigned successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while assigning role: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn(ADMIN_NOT_FOUND_LOG, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error assigning role: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to assign role")
                            .build());
        }
    }

    /**
     * Get admin by ID
     * Required permission: VIEW_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_ADMIN')")
    public ResponseEntity<APIResponse<AdminDetailsResponseDTO>> getAdminById(@PathVariable Long id) {
        log.info("Fetching admin by ID: {}", id);

        try {
            AdminDetailsResponseDTO response = adminService.getAdminById(id);

            return ResponseEntity
                    .ok(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Admin retrieved successfully")
                            .data(response)
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn(ADMIN_NOT_FOUND_LOG, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error fetching admin: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch admin")
                            .build());
        }
    }

    /**
     * Get admin by UUID
     * Required permission: VIEW_ADMIN
     */
    @GetMapping("/uuid/{uuid}")
    @PreAuthorize("hasAuthority('VIEW_ADMIN')")
    public ResponseEntity<APIResponse<AdminDetailsResponseDTO>> getAdminByUuid(@PathVariable String uuid) {
        log.info("Fetching admin by UUID: {}", uuid);

        try {
            AdminDetailsResponseDTO response = adminService.getAdminByUuid(uuid);

            return ResponseEntity
                    .ok(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Admin retrieved successfully")
                            .data(response)
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn(ADMIN_NOT_FOUND_LOG, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error fetching admin: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch admin")
                            .build());
        }
    }

    /**
     * Get all admins with pagination
     * Required permission: VIEW_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ADMIN')")
    public ResponseEntity<APIResponse<PageResponse<AdminDetailsResponseDTO>>> getAllAdmins(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION) String sortDir) {

        log.info("Fetching all admins - page: {}, size: {}", page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();

            Pageable pageable = PageRequest.of(page, size, sort);
            PageResponse<AdminDetailsResponseDTO> response = adminService.getAllAdmins(pageable);

            return ResponseEntity
                    .ok(APIResponse.<PageResponse<AdminDetailsResponseDTO>>builder()
                            .responseCode(ResponseCode.SUCCESS.getCode())
                            .success(true)
                            .message("Admins retrieved successfully")
                            .data(response)
                            .build());

        } catch (Exception e) {
            log.error("Error fetching admins: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<PageResponse<AdminDetailsResponseDTO>>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch admins")
                            .build());
        }
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('VIEW_ADMIN')")
    public ResponseEntity<APIResponse<PageResponse<CustomerDetailsLookupResponse>>> getAllCustomerDetails(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "true") boolean includeAddresses) {

        log.info("Admin: Fetching customer details - page: {}, size: {}, includeAddresses: {}", page, size, includeAddresses);

        try {
            PageResponse<CustomerDetailsLookupResponse> pageResponseData = adminService.getAllCustomerDetails(page, size, includeAddresses);

            if (pageResponseData == null || pageResponseData.getContent() == null || pageResponseData.getContent().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(APIResponse.<PageResponse<CustomerDetailsLookupResponse>>builder()
                                .responseCode(ResponseCode.NOT_FOUND.getCode())
                                .success(false)
                                .message("No customers found")
                                .build());
            }

            return ResponseEntity.ok(APIResponse.<PageResponse<CustomerDetailsLookupResponse>>builder()
                    .responseCode(ResponseCode.SUCCESS.getCode())
                    .success(true)
                    .message("Customer details retrieved successfully")
                    .data(pageResponseData)
                    .build());

        } catch (Exception e) {
            log.error("Error fetching customer details: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<PageResponse<CustomerDetailsLookupResponse>>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch customer details")
                            .build());
        }
    }

    /**
     * New endpoint: Get all admin details (paginated)
     * Required permission: VIEW_ADMIN
     */
    @GetMapping("/details")
    public ResponseEntity<APIResponse<PageResponse<AdminDetailsResponseDTO>>> getAllAdminDetails() {
        log.info("Fetching paginated admin details via /details");

        try {
            PageResponse<AdminDetailsResponseDTO> response = adminService.getAllAdminDetails();

            return ResponseEntity.ok(APIResponse.<PageResponse<AdminDetailsResponseDTO>>builder()
                    .responseCode(ResponseCode.SUCCESS.getCode())
                    .success(true)
                    .message("Admin details retrieved successfully")
                    .data(response)
                    .build());

        } catch (Exception e) {
            log.error("Error fetching paginated admin details: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<PageResponse<AdminDetailsResponseDTO>>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to fetch admin details")
                            .build());
        }
    }

    /**
     * Update admin details
     * Required permission: EDIT_ADMIN
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EDIT_ADMIN')")
    public ResponseEntity<APIResponse<AdminDetailsResponseDTO>> updateAdmin(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Admin update request for ID: {} from: {}", id, userDetails.getUsername());

        try {
            AdminDetailsResponseDTO response = adminService.updateAdmin(id, request, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Admin updated successfully")
                            .data(response)
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn(ADMIN_NOT_FOUND_LOG, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error updating admin: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to update admin")
                            .build());
        }
    }
    @PutMapping("/update-password/{id}")
    @PreAuthorize("hasAuthority('EDIT_ADMIN')")
    public ResponseEntity<APIResponse<AdminDetailsResponseDTO>> updatePassword(
            @PathVariable Long id,
            @RequestBody AdminUpdatePasswordDTO request) {


        try {
            AdminDetailsResponseDTO response = adminService.updatePassword(id, request);

            return ResponseEntity
                    .ok(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.UPDATED.getCode())
                            .success(true)
                            .message("Admin password updated successfully")
                            .data(response)
                            .build());

        } catch (BadRequestException e) {
            log.warn("Bad request while updating admin password: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.<AdminDetailsResponseDTO>builder()
                            .responseCode(ResponseCode.BAD_REQUEST.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        }
    }

    /**
     * Deactivate admin (soft delete)
     * Required permission: DELETE_ADMIN
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_ADMIN')")
    public ResponseEntity<APIResponse<Void>> deactivateAdmin(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Admin deactivation request for ID: {} from: {}", id, userDetails.getUsername());

        try {
            adminService.deactivateAdmin(id, userDetails.getUsername());

            return ResponseEntity
                    .ok(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.DELETED.getCode())
                            .success(true)
                            .message("Admin deactivated successfully")
                            .build());

        } catch (ResourceNotFoundException e) {
            log.warn(ADMIN_NOT_FOUND_LOG, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.NOT_FOUND.getCode())
                            .success(false)
                            .message(e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Error deactivating admin: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponse.<Void>builder()
                            .responseCode(ResponseCode.INTERNAL_ERROR.getCode())
                            .success(false)
                            .message("Failed to deactivate admin")
                            .build());
        }
    }
}