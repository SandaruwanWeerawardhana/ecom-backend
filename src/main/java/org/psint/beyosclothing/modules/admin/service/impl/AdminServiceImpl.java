package org.psint.beyosclothing.modules.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.admin.dto.request.AdminCreationRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AdminUpdatePasswordDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AdminUpdateRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.request.AssignRoleRequestDTO;
import org.psint.beyosclothing.modules.admin.dto.response.AdminDetailsResponseDTO;
import org.psint.beyosclothing.modules.admin.dto.response.RoleInfoDTO;
import org.psint.beyosclothing.modules.admin.dto.response.UserInfoDTO;
import org.psint.beyosclothing.modules.admin.entity.AdminEntity;
import org.psint.beyosclothing.modules.admin.events.DeactivateAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.DeleteAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminPasswordEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminUserDetailsEvent;
import org.psint.beyosclothing.modules.admin.repository.AdminRepository;
import org.psint.beyosclothing.modules.admin.service.AdminEventPublisherService;
import org.psint.beyosclothing.modules.admin.service.AdminService;
import org.psint.beyosclothing.modules.admin.service.AuthQueryService;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupRequest;
import org.psint.beyosclothing.modules.customers.dto.external.CustomerDetailsLookupResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin Service Implementation
 * Handles admin user management operations
 * ✅ FIXED: No direct Auth repository access - uses AuthQueryService instead
 * ✅ MODULAR: Admin module is now properly isolated from Auth module database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final AuthQueryService authQueryService; // ✅ Use service instead of repositories
    private final PasswordEncoder passwordEncoder;
    private final AdminEventPublisherService eventPublisher;

    private final RabbitTemplate rabbitTemplate;

    /**
     * Dedicated RabbitTemplate for Auth module RPC calls with extended timeout.
     */
    private final RabbitTemplate authRpcRabbitTemplate;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchange;

    @Value("${app.rabbitmq.routing-keys.customer-details-request:customer.details.request}")
    private String customerDetailsRoutingKey;

    @Value("${app.rabbitmq.exchange.auth}")
    private String authExchange;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional("adminTransactionManager")
    public AdminDetailsResponseDTO createAdmin(AdminCreationRequestDTO request, String createdBy) {
        log.info("Creating admin with email: {}", request.getEmail());

        String email = request.getEmail();
        boolean userCreated = false;

        try {
            // Check if email already exists (via service)
            if (authQueryService.emailExists(email)) {
                throw new BadRequestException("Email already exists");
            }

            // Check if email exists but is deactivated - if so, allow creation (will create new record)
            UserInfoDTO existingUser = authQueryService.getUserByEmail(email).orElse(null);
            if (existingUser != null) {
                log.info("Found existing user with email '{}' - Active: {}", email, existingUser.getIsActive());
                if (!existingUser.getIsActive()) {
                    log.info("Email '{}' is deactivated - creating NEW user record", email);
                }
            }

            // Validate role exists and is admin role (via service)
            RoleInfoDTO role = authQueryService.getRoleByCode(request.getRoleCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleCode()));

            if (!"ADMIN".equals(role.getUserType())) {
                throw new BadRequestException("Selected role is not an admin role");
            }

            // Prefer RPC for user creation to avoid async event timeouts
            try {
                Map<String, Object> userRequest = new HashMap<>();
                userRequest.put("email", email);
                userRequest.put("password", passwordEncoder.encode(request.getPassword()));
                userRequest.put("firstName", request.getFirstName());
                userRequest.put("lastName", request.getLastName());
                userRequest.put("userType", "ADMIN");
                userRequest.put("emailVerified", true); // Admin emails auto-verified
                userRequest.put("accountLocked", false);
                userRequest.put("roleCode", request.getRoleCode());

                Object rpcResponse = authRpcRabbitTemplate.convertSendAndReceive(
                        authExchange,
                        "auth.user.create.rpc",
                        userRequest
                );

                if (rpcResponse == null) {
                    throw new ResourceNotFoundException("User creation failed - no response from Auth service. Please try again.");
                }

                if (rpcResponse instanceof Map<?, ?> respMap) {
                    Object successObj = respMap.get("success");
                    boolean success = successObj instanceof Boolean && (Boolean) successObj;

                    if (!success) {
                        Object err = respMap.get("error");
                        throw new ResourceNotFoundException("Auth service failed to create user: " + (err != null ? err.toString() : "unknown error"));
                    }

                    Number userIdNum = (Number) respMap.get("userId");
                    if (userIdNum == null) {
                        throw new ResourceNotFoundException("Auth service created user but did not return userId");
                    }

                    UserInfoDTO createdUser = UserInfoDTO.builder().id(userIdNum.longValue()).build();
                    userCreated = true;
                    log.info("User created synchronously in Auth DB with ID: {}", createdUser.getId());
                    // Create admin entity in admin DB referencing createdUser
                    AdminEntity admin = AdminEntity.builder()
                            .userId(createdUser.getId())
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .email(email)
                            .phone(request.getPhone())
                            .build();

                    admin = adminRepository.save(admin);
                    log.info("✅ Admin created successfully - ID: {}, UUID: {}, UserID: {}",
                            admin.getId(), admin.getUuid(), admin.getUserId());

                    return mapToResponseDTO(admin, role);
                } else {
                    throw new ResourceNotFoundException("Invalid response from Auth service during user creation");
                }
            } catch (ResourceNotFoundException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error creating user in Auth service via RPC: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to create user in Auth service: " + e.getMessage(), e);
            }



        } catch (ResourceNotFoundException | BadRequestException e) {
            // Rollback: Delete user if it was created but admin entity creation failed
            if (userCreated) {
                log.warn("Rolling back user creation due to error: {}", e.getMessage());
                rollbackUserCreation(email, "Admin entity creation failed: " + e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating admin: {}", e.getMessage(), e);

            // Rollback: Delete user if it was created
            if (userCreated) {
                log.warn("Rolling back user creation due to unexpected error");
                rollbackUserCreation(email, "Unexpected error during admin creation: " + e.getMessage());
            }

            throw new RuntimeException("Failed to create admin: " + e.getMessage());
        }
    }

    /**
     * Rollback user creation by publishing delete event
     */
    private void rollbackUserCreation(String email, String reason) {
        try {
            DeleteAdminUserEvent deleteEvent = DeleteAdminUserEvent.builder()
                    .email(email)
                    .reason(reason)
                    .deletedBy("SYSTEM_ROLLBACK")
                    .deletedAt(LocalDateTime.now())
                    .build();

            eventPublisher.publishDeleteAdminUserEvent(deleteEvent);
            log.info("Published DeleteAdminUserEvent for rollback: {}", email);
        } catch (Exception e) {
            log.error("Failed to publish rollback event for {}: {}", email, e.getMessage());
        }
    }

    @Override
    @Transactional("adminTransactionManager")
    public AdminDetailsResponseDTO assignRoleToAdmin(AssignRoleRequestDTO request, String assignedBy) {
        log.info("Assigning role {} to admin {}", request.getRoleCode(), request.getAdminId());

        try {
            // Get admin entity
            AdminEntity admin = adminRepository.findById(request.getAdminId())
                    .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

            // Validate role exists and is admin role (via service)
            RoleInfoDTO role = authQueryService.getRoleByCode(request.getRoleCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleCode()));

            if (!"ADMIN".equals(role.getUserType())) {
                throw new BadRequestException("Selected role is not an admin role");
            }

            // ✅ Publish event to update user role in Auth DB
            UpdateAdminRoleEvent roleEvent = UpdateAdminRoleEvent.builder()
                    .userId(admin.getUserId())
                    .roleCode(request.getRoleCode())
                    .updatedBy(assignedBy)
                    .build();

            eventPublisher.publishUpdateAdminRoleEvent(roleEvent);
            log.info("Published UpdateAdminRoleEvent for userId: {}", admin.getUserId());

            // Wait for event processing
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            adminRepository.save(admin);

            log.info("Role assigned successfully to admin {}", request.getAdminId());
            return mapToResponseDTO(admin, role);

        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error assigning role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to assign role: " + e.getMessage());
        }
    }

    @Override
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public AdminDetailsResponseDTO getAdminById(Long adminId) {
        log.info("Fetching admin by ID: {}", adminId);

        AdminEntity admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));

        UserInfoDTO user = authQueryService.getUserById(admin.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RoleInfoDTO role = user.getUserRoleId() != null
                ? authQueryService.getRoleById(user.getUserRoleId()).orElse(null)
                : null;

        return mapToResponseDTO(admin, role);
    }

    @Override
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public AdminDetailsResponseDTO getAdminByUserId(Long userId) {
        log.info("Fetching admin by user ID: {}", userId);

        AdminEntity admin = adminRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with user ID: " + userId));

        UserInfoDTO user = authQueryService.getUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RoleInfoDTO role = user.getUserRoleId() != null
                ? authQueryService.getRoleById(user.getUserRoleId()).orElse(null)
                : null;

        return mapToResponseDTO(admin, role);
    }

    @Override
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public AdminDetailsResponseDTO getAdminByUuid(String uuid) {
        log.info("Fetching admin by UUID: {}", uuid);

        AdminEntity admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with UUID: " + uuid));

        UserInfoDTO user = authQueryService.getUserById(admin.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RoleInfoDTO role = user.getUserRoleId() != null
                ? authQueryService.getRoleById(user.getUserRoleId()).orElse(null)
                : null;

        return mapToResponseDTO(admin, role);
    }

    @Override
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public PageResponse<AdminDetailsResponseDTO> getAllAdmins(Pageable pageable) {
        log.info("Fetching all admins with pagination");

        Page<AdminEntity> adminPage = adminRepository.findAll(pageable);

        List<AdminDetailsResponseDTO> content = adminPage.getContent().stream()
                .map(admin -> {
                    UserInfoDTO user = authQueryService.getUserById(admin.getUserId()).orElse(null);
                    RoleInfoDTO role = user != null && user.getUserRoleId() != null
                            ? authQueryService.getRoleById(user.getUserRoleId()).orElse(null)
                            : null;
                    return mapToResponseDTO(admin, role);
                })
                .collect(Collectors.toList());

        return PageResponse.<AdminDetailsResponseDTO>builder()
                .content(content)
                .pageNumber(adminPage.getNumber())
                .pageSize(adminPage.getSize())
                .totalElements(adminPage.getTotalElements())
                .totalPages(adminPage.getTotalPages())
                .last(adminPage.isLast())
                .first(adminPage.isFirst())
                .empty(adminPage.isEmpty())
                .build();
    }

    @Override
    @Transactional("adminTransactionManager")
    public AdminDetailsResponseDTO updateAdmin(Long adminId, AdminUpdateRequestDTO request, String updatedBy) {
        log.info("Updating admin with ID: {}", adminId);

        try {
            AdminEntity admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

            String currentEmail = admin.getEmail();

            // Update admin details
            admin.setFirstName(request.getFirstName());
            admin.setLastName(request.getLastName());
            admin.setEmail(request.getEmail());
            admin.setPhone(request.getPhone());
            admin = adminRepository.save(admin);

            if (!currentEmail.equals(request.getEmail())) {
                log.info("Email updated for admin {}, syncing to Auth DB", adminId);

                UpdateAdminUserDetailsEvent detailsEvent = UpdateAdminUserDetailsEvent.builder()
                        .userId(admin.getUserId())
                        .email(request.getEmail())
                        .updatedBy(updatedBy)
                        .updatedAt(LocalDateTime.now())
                        .build();

                eventPublisher.publishUpdateAdminUserDetailsEvent(detailsEvent);
                log.info("Published UpdateAdminUserDetailsEvent for userId: {}", admin.getUserId());
            }

            // Get current user and role (via service)
            UserInfoDTO user = authQueryService.getUserById(admin.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            RoleInfoDTO currentRole = user.getUserRoleId() != null
                    ? authQueryService.getRoleById(user.getUserRoleId()).orElse(null)
                    : null;

            // Handle password update
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                log.info("Password updated for admin {}, syncing to Auth DB", adminId);

                String encodedPassword = passwordEncoder.encode(request.getPassword());

                UpdateAdminPasswordEvent passwordEvent = UpdateAdminPasswordEvent.builder()
                        .userId(admin.getUserId())
                        .password(encodedPassword)
                        .updatedBy(updatedBy)
                        .updatedAt(LocalDateTime.now())
                        .build();

                eventPublisher.publishUpdateAdminPasswordEvent(passwordEvent);
                log.info("Published UpdateAdminPasswordEvent for userId: {}", admin.getUserId());
            }

            // Handle role update
            if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
                if (currentRole == null || !currentRole.getRoleCode().equals(request.getRoleCode())) {
                    RoleInfoDTO newRole = authQueryService.getRoleByCode(request.getRoleCode())
                            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleCode()));

                    if (!"ADMIN".equals(newRole.getUserType())) {
                        throw new BadRequestException("Selected role is not an admin role");
                    }

                    UpdateAdminRoleEvent roleEvent = UpdateAdminRoleEvent.builder()
                            .userId(admin.getUserId())
                            .roleCode(request.getRoleCode())
                            .updatedBy(updatedBy)
                            .build();

                    eventPublisher.publishUpdateAdminRoleEvent(roleEvent);
                    log.info("Published UpdateAdminRoleEvent for userId: {} to role: {}", admin.getUserId(), request.getRoleCode());

                    currentRole = newRole;
                }
            }

            log.info("Admin updated successfully: {}", adminId);
            return mapToResponseDTO(admin, currentRole);

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update admin: " + e.getMessage());
        }
    }

    @Override
    @Transactional("adminTransactionManager")
    public AdminDetailsResponseDTO updatePassword(Long adminId, AdminUpdatePasswordDTO request) {
        try {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new BadRequestException("Current password is required");
            }
            if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
                throw new BadRequestException("New password is required");
            }
            if (request.getCurrentPassword().equals(request.getNewPassword())) {
                throw new BadRequestException("New password must be different from current password");
            }

            AdminEntity admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("userId", admin.getUserId());
            verifyRequest.put("currentPassword", request.getCurrentPassword());

            Object verifyResponse = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    "auth.user.verify.password.rpc",
                    verifyRequest
            );

            boolean isCurrentPasswordValid = false;
            if (verifyResponse instanceof Map<?, ?> responseMap) {
                Object successObj = responseMap.get("success");
                isCurrentPasswordValid = successObj instanceof Boolean && (Boolean) successObj;
            }

            if (!isCurrentPasswordValid) {
                throw new BadRequestException("Current password is incorrect");
            }

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("userId", admin.getUserId());
            updateRequest.put("newPassword", passwordEncoder.encode(request.getNewPassword()));

            Object updateResponse = authRpcRabbitTemplate.convertSendAndReceive(
                    authExchange,
                    "auth.user.update.password.rpc",
                    updateRequest
            );

            boolean updateSuccess = false;
            if (updateResponse instanceof Map<?, ?> responseMap) {
                Object successObj = responseMap.get("success");
                updateSuccess = successObj instanceof Boolean && (Boolean) successObj;
            }

            if (!updateSuccess) {
                throw new RuntimeException("Failed to update password in auth service");
            }

            UserInfoDTO user = authQueryService.getUserById(admin.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            RoleInfoDTO role = user.getUserRoleId() != null
                    ? authQueryService.getRoleById(user.getUserRoleId()).orElse(null)
                    : null;

            log.info("Admin password updated successfully for admin ID: {}", adminId);
            return mapToResponseDTO(admin, role);

        } catch (BadRequestException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating admin password for ID {}: {}", adminId, e.getMessage(), e);
            throw new RuntimeException("Failed to update admin password: " + e.getMessage());
        }
    }

    @Override
    @Transactional("adminTransactionManager")
    public void deactivateAdmin(Long adminId, String deactivatedBy) {
        log.info("Deactivating admin with ID: {}", adminId);

        try {
            AdminEntity admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

            admin.setIsActive(false);
            adminRepository.save(admin);

            // Deactivate the corresponding user in Auth DB via event
            DeactivateAdminUserEvent deactivateEvent = DeactivateAdminUserEvent.builder()
                    .userId(admin.getUserId())
                    .deactivatedBy(deactivatedBy)
                    .deactivatedAt(LocalDateTime.now())
                    .build();
            eventPublisher.publishDeactivateAdminUserEvent(deactivateEvent);

            log.info("Admin deactivated successfully: {} (User ID: {} also deactivated)", adminId, admin.getUserId());

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deactivating admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to deactivate admin: " + e.getMessage());
        }
    }

    @Override
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public boolean hasPermission(Long userId, String permissionCode) {
        log.debug("Checking permission {} for user {}", permissionCode, userId);
        return authQueryService.hasPermission(userId, permissionCode);
    }

    @Override
    public PageResponse<CustomerDetailsLookupResponse> getAllCustomerDetails(int page, int size, boolean includeAddresses) {
        log.info("AdminService: fetching customer details via RabbitMQ - page={}, size={}, includeAddresses={}", page, size, includeAddresses);

        try {
            CustomerDetailsLookupRequest request = CustomerDetailsLookupRequest.builder()
                    .requestId(java.util.UUID.randomUUID().toString())
                    .page(page)
                    .size(size)
                    .includeAddresses(includeAddresses)
                    .build();

            Object rawResponse = rabbitTemplate.convertSendAndReceive(customerExchange, customerDetailsRoutingKey, request);

            if (rawResponse == null) {
                log.warn("No response from customer module for requestId={}", request.getRequestId());
                return PageResponse.<CustomerDetailsLookupResponse>builder()
                        .content(java.util.Collections.emptyList())
                        .pageNumber(page)
                        .pageSize(size)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .first(true)
                        .empty(true)
                        .build();
            }

            // Expect APIResponse<PageResponse<CustomerDetailsLookupResponse>> or raw PageResponse
            PageResponse<CustomerDetailsLookupResponse> pageResponse;

            if (rawResponse instanceof APIResponse<?> apiResp) {
                Object data = apiResp.getData();
                String json = objectMapper.writeValueAsString(data);
                pageResponse = objectMapper.readValue(json, objectMapper.getTypeFactory().constructParametricType(PageResponse.class, CustomerDetailsLookupResponse.class));
            } else {
                String json = objectMapper.writeValueAsString(rawResponse);
                pageResponse = objectMapper.readValue(json, objectMapper.getTypeFactory().constructParametricType(PageResponse.class, CustomerDetailsLookupResponse.class));
            }

            return pageResponse;

        } catch (Exception e) {
            log.error("Error fetching customer details via RabbitMQ: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to fetch customer details: " + e.getMessage());
        }
    }

    private AdminDetailsResponseDTO mapToResponseDTO(AdminEntity admin, RoleInfoDTO role) {
        return AdminDetailsResponseDTO.builder()
                .id(admin.getId())
                .uuid(admin.getUuid())
                .userId(admin.getUserId())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .fullName(admin.getFullName())
                .email(admin.getEmail())
                .phone(admin.getPhone())
                .role(role)
                .isActive(admin.getIsActive())
                .createdAt(admin.getDateCreated())
                .updatedAt(admin.getDateUpdated())
                .build();
    }

    /**
     * Returns admin details for admins whose role is SUPER_ADMIN OR CASHIER (no pagination).
     */
    @Override
    @Transactional(value = "adminTransactionManager", readOnly = true)
    public PageResponse<AdminDetailsResponseDTO> getAllAdminDetails() {
        log.info("Fetching admin details for SUPER_ADMIN or CASHIER roles");

        List<String> targetRoleCodes = List.of("SUPER_ADMIN", "CASHIER");
        Map<String, AuthQueryService.RolePermissionResult> roleDataMap =
                authQueryService.getRolesWithPermissions(targetRoleCodes);

        List<Long> allUserIds = new ArrayList<>();
        for (AuthQueryService.RolePermissionResult result : roleDataMap.values()) {
            allUserIds.addAll(result.userIds());
        }

        if (allUserIds.isEmpty()) {
            log.warn("No users found for roles={} (roles in map={})", targetRoleCodes, roleDataMap.keySet());
            return PageResponse.<AdminDetailsResponseDTO>builder()
                    .content(List.of())
                    .pageNumber(0)
                    .pageSize(0)
                    .totalElements(0).totalPages(0)
                    .last(true).first(true).empty(true)
                    .build();
        }


        List<AdminEntity> admins = adminRepository.findByUserIdIn(allUserIds);

        Map<Long, AuthQueryService.RolePermissionResult> userIdToRoleData = new HashMap<>();
        for (AuthQueryService.RolePermissionResult rpr : roleDataMap.values()) {
            for (Long uid : rpr.userIds()) {
                userIdToRoleData.put(uid, rpr);
            }
        }

        List<AdminDetailsResponseDTO> content = admins.stream()
                .map(admin -> {
                    AuthQueryService.RolePermissionResult rpr = userIdToRoleData.get(admin.getUserId());
                    RoleInfoDTO role = rpr != null ? rpr.role() : null;
                    return mapToResponseDTO(admin, role);
                })
                .toList();

        return PageResponse.<AdminDetailsResponseDTO>builder()
                .content(content)
                .pageNumber(0)
                .pageSize(content.size())
                .totalElements(content.size())
                .totalPages(content.isEmpty() ? 0 : 1)
                .last(true)
                .first(true)
                .empty(content.isEmpty())
                .build();
    }
}

