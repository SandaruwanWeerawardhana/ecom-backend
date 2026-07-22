package org.psint.beyosclothing.modules.auth.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.events.AssignPermissionsToRoleEvent;
import org.psint.beyosclothing.modules.admin.events.CreateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.CreateAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.DeactivateAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.InitializePermissionsEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminPasswordEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminUserDetailsEvent;
import org.psint.beyosclothing.modules.auth.entity.RolePermission;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.entity.UserPermission;
import org.psint.beyosclothing.modules.auth.entity.UserRole;
import org.psint.beyosclothing.modules.auth.repository.RolePermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserPermissionRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.auth.repository.UserRoleRepository;
import org.psint.beyosclothing.modules.auth.service.UsernameGeneratorService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admin Event Consumer in Auth Module
 * Consumes events from Admin module and manages permissions/roles in Auth DB
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminEventConsumer {

    private final UserPermissionRepository userPermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final UsernameGeneratorService usernameGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Listen to admin user creation events
     */
    @RabbitListener(queues = "admin.user.create.queue")
    @Transactional("authTransactionManager")
    public void handleCreateAdminUser(CreateAdminUserEvent event) {
        log.info("Received CreateAdminUserEvent for email: {}", event.getEmail());

        try {
            // Check if user already exists
            if (userRepository.existsByActiveEmail(event.getEmail())) {
                log.warn("User already exists with email: {}", event.getEmail());
                return;
            }

            // Find role
            UserRole role = userRoleRepository.findByRoleCode(event.getRoleCode())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + event.getRoleCode()));

            // Generate username
            String generatedUsername = usernameGenerator.generateUsername("ADMIN");

            // Create user
            User user = User.builder()
                    .username(generatedUsername)
                    .email(event.getEmail())
                    .password(event.getPassword()) // Already encrypted
                    .userType("ADMIN")
                    .userRoleId(role.getId())
                    .emailVerified(true) // Auto-verify admin emails
                    .accountLocked(false)
                    .loginAttempts(0)
                    .build();

            userRepository.save(user);
            log.info("✅ Admin user created successfully: {}", event.getEmail());

        } catch (Exception e) {
            log.error("Error creating admin user: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to admin role update events
     */
    @RabbitListener(queues = "admin.user.role.update.queue")
    @Transactional("authTransactionManager")
    public void handleUpdateAdminRole(UpdateAdminRoleEvent event) {
        log.info("Received UpdateAdminRoleEvent for userId: {}", event.getUserId());

        try {
            // Find user
            User user = userRepository.findById(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Find role
            UserRole role = userRoleRepository.findByRoleCode(event.getRoleCode())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + event.getRoleCode()));

            // Update user role
            user.setUserRoleId(role.getId());
            userRepository.save(user);

            log.info("✅ Admin role updated successfully for userId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("❌ Error updating admin role: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to admin password update events.
     */
    @RabbitListener(queues = "admin.user.password.update.queue")
    @Transactional("authTransactionManager")
    public void handleUpdateAdminPassword(UpdateAdminPasswordEvent event) {
        log.info("Received UpdateAdminPasswordEvent for userId: {}", event.getUserId());

        try {
            User user = userRepository.findById(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setPassword(event.getPassword());
            userRepository.save(user);

            log.info("✅ Admin password updated successfully for userId: {}", event.getUserId());
        } catch (Exception e) {
            log.error("❌ Error updating admin password: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to admin user details update events (email)
     */
    @RabbitListener(queues = "admin.user.details.update.queue")
    @Transactional("authTransactionManager")
    public void handleUpdateAdminUserDetails(UpdateAdminUserDetailsEvent event) {
        log.info("Received UpdateAdminUserDetailsEvent for userId: {}", event.getUserId());

        try {
            User user = userRepository.findById(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));

            // Update email
            user.setEmail(event.getEmail());
            userRepository.save(user);

            log.info("✅ Admin user details updated successfully for userId: {} - Email: {}",
                    event.getUserId(), event.getEmail());

        } catch (Exception e) {
            log.error("❌ Error updating admin user details: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to permission initialization events
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.admin-permissions-initialize:admin.permissions.initialize.queue}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional("authTransactionManager")
    public void handleInitializePermissions(InitializePermissionsEvent event) {
        log.info("Received InitializePermissionsEvent: {}", event.getRequestId());

        try {
            // Check if permissions already exist
//            List<UserPermission> existingPermissions = userPermissionRepository.findAllParentPermissions();
//            if (!existingPermissions.isEmpty()) {
//                log.info("✅ Permissions already initialized. Skipping... (Found {} parent permissions)", existingPermissions.size());
//                return;
//            }

            initializePermissions();
            log.info("✅ Permissions initialized successfully via event");

        } catch (Exception e) {
            log.error("❌ Error initializing permissions: {}", e.getMessage(), e);
            throw e; // Re-throw to trigger retry mechanism
        }
    }

    /**
     * Listen to create admin role events
     */
    @RabbitListener(queues = "admin.role.create.queue")
    @Transactional("authTransactionManager")
    public void handleCreateAdminRole(Message message) {
        log.info("Received CreateAdminRoleEvent message");

        try {
            // Deserialize message to event
            CreateAdminRoleEvent event = objectMapper.readValue(message.getBody(), CreateAdminRoleEvent.class);
            log.info("Deserialized CreateAdminRoleEvent: {}", event.getRoleCode());

            // Check if role already exists
            if (userRoleRepository.existsByRoleCode(event.getRoleCode())) {
                log.warn("Role already exists: {}", event.getRoleCode());
                return;
            }

            // Create role
            UserRole role = UserRole.builder()
                    .roleCode(event.getRoleCode())
                    .roleName(event.getRoleName())
                    .description(event.getDescription())
                    .userType(event.getUserType())
                    .build();
            role = userRoleRepository.save(role);

            // Assign permissions if provided
            if (event.getPermissionCodes() != null && !event.getPermissionCodes().isEmpty()) {
                assignPermissionsToRole(role, event.getPermissionCodes());
            }

            log.info("✅ Admin role created successfully: {}", event.getRoleCode());

        } catch (Exception e) {
            log.error("❌ Error creating admin role: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to assign permissions events
     */
    @RabbitListener(queues = "admin.permissions.assign.queue")
    @Transactional("authTransactionManager")
    public void handleAssignPermissions(Message message) {
        log.info("Received AssignPermissionsToRoleEvent message");

        try {
            // Deserialize message to event
            AssignPermissionsToRoleEvent event = objectMapper.readValue(message.getBody(), AssignPermissionsToRoleEvent.class);
            log.info("Deserialized AssignPermissionsToRoleEvent for role: {}", event.getRoleCode());

            // Find role by code
            UserRole role = userRoleRepository.findByRoleCode(event.getRoleCode())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + event.getRoleCode()));

            // Delete existing permissions
            rolePermissionRepository.deleteByRoleId(role.getId());

            // Assign new permissions
            assignPermissionsToRole(role, event.getPermissionCodes());

            log.info("✅ Permissions assigned successfully to role: {}", event.getRoleCode());

        } catch (Exception e) {
            log.error("❌ Error assigning permissions: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to admin user deactivation events
     * Sets isActive = false on the User entity in Auth DB
     */
    @RabbitListener(queues = "admin.user.deactivate.queue")
    @Transactional("authTransactionManager")
    public void handleDeactivateAdminUser(DeactivateAdminUserEvent event) {
        log.info("Received DeactivateAdminUserEvent for userId: {}", event.getUserId());

        try {
            User user = userRepository.findById(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));

            user.setIsActive(false);
            userRepository.save(user);

            log.info("✅ Admin user deactivated successfully in Auth DB for userId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("❌ Error deactivating admin user in Auth DB: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to reseller user deactivation messages
     * Sets isActive = false on the matching RESELLER user(s) in Auth DB
     */
    @RabbitListener(queues = "auth.user.deactivate.queue")
    @Transactional("authTransactionManager")
    public void handleDeactivateResellerUser(Map<String, Object> request) {
        String email = (String) request.get("email");
        String userType = (String) request.getOrDefault("userType", "RESELLER");

        log.info("Received reseller user deactivate request for email: {}, userType: {}", email, userType);

        try {
            List<User> users = userRepository.findAllByEmailIgnoreCaseAndUserTypeIgnoreCaseAndIsActiveTrue(email, userType);

            if (users.isEmpty()) {
                log.info("No active {} user found for email: {}", userType, email);
                return;
            }

            users.forEach(user -> user.setIsActive(false));
            userRepository.saveAll(users);

            log.info("{} user(s) deactivated successfully in Auth DB for email: {}", users.size(), email);

        } catch (Exception e) {
            log.error(" Error deactivating reseller user in Auth DB: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to user account unlock events
     * Handles unlock requests from reseller approval process
     */
    @RabbitListener(queues = "auth.user.unlock.queue")
    @Transactional("authTransactionManager")
    public void handleUserUnlock(Map<String, Object> request) {
        Long userId = ((Number) request.get("userId")).longValue();
        log.info("Received user unlock request for userId: {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            user.setAccountLocked(false);
            user.setLoginAttempts(0); // Reset login attempts
            userRepository.save(user);

            log.info("✅ User account unlocked successfully: {}", userId);

        } catch (Exception e) {
            log.error("❌ Error unlocking user account: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to user account lock events
     * Handles lock requests from reseller suspension/rejection
     */
    @RabbitListener(queues = "auth.user.lock.queue")
    @Transactional("authTransactionManager")
    public void handleUserLock(Map<String, Object> request) {
        Long userId = ((Number) request.get("userId")).longValue();
        log.info("Received user lock request for userId: {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            user.setAccountLocked(true);
            userRepository.save(user);

            log.info("✅ User account locked successfully: {}", userId);

        } catch (Exception e) {
            log.error("❌ Error locking user account: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize hierarchical permissions
     */
    private void initializePermissions() {

        // 1. ADMIN MANAGEMENT PERMISSIONS
        UserPermission adminManage = createParentPermission("ADMIN_MANAGE", "Admin Management",
                "Manage admin staff and roles", "ADMIN", 1);
        adminManage = userPermissionRepository.save(adminManage); // ✅ Save and get ID

        // Save sub-permissions immediately with parent reference
        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("CREATE_ADMIN", "Create Admin", "Create new admin staff", "ADMIN", adminManage, 1),
                createSubPermission("EDIT_ADMIN", "Edit Admin", "Edit admin staff details", "ADMIN", adminManage, 2),
                createSubPermission("DELETE_ADMIN", "Delete Admin", "Delete admin staff", "ADMIN", adminManage, 3),
                createSubPermission("VIEW_ADMIN", "View Admin", "View admin staff details", "ADMIN", adminManage, 4),
                createSubPermission("EDIT_ADMIN_PASSWORD", "Edit Admin Password", "Change admin password", "ADMIN", adminManage, 5),
                createSubPermission("ASSIGN_ADMIN_ROLE", "Assign Admin Role", "Assign roles to admin staff", "ADMIN", adminManage, 6)
        ));

        // 2. ROLE MANAGEMENT PERMISSIONS
        UserPermission roleManage = createParentPermission("ROLE_MANAGE", "Role Management",
                "Manage admin roles and permissions", "ADMIN", 2);
        roleManage = userPermissionRepository.save(roleManage); // ✅ Save and get ID

        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("CREATE_ROLE", "Create Role", "Create new admin roles", "ADMIN", roleManage, 1),
                createSubPermission("EDIT_ROLE", "Edit Role", "Edit role details", "ADMIN", roleManage, 2),
                createSubPermission("DELETE_ROLE", "Delete Role", "Delete admin roles", "ADMIN", roleManage, 3),
                createSubPermission("VIEW_ROLE", "View Role", "View role details", "ADMIN", roleManage, 4),
                createSubPermission("ASSIGN_PERMISSION", "Assign Permission", "Assign permissions to roles", "ADMIN", roleManage, 5)
        ));

        // 3. CUSTOMER MANAGEMENT PERMISSIONS
        UserPermission customerManage = createParentPermission("CUSTOMER_MANAGE", "Customer Management",
                "Manage customer accounts", "CUSTOMER", 3);
        customerManage = userPermissionRepository.save(customerManage); // ✅ Save and get ID

        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("VIEW_CUSTOMER", "View Customer", "View customer details", "CUSTOMER", customerManage, 1),
                createSubPermission("EDIT_CUSTOMER", "Edit Customer", "Edit customer information", "CUSTOMER", customerManage, 2),
                createSubPermission("BLOCK_CUSTOMER", "Block Customer", "Block/unblock customer accounts", "CUSTOMER", customerManage, 3),
                createSubPermission("VIEW_CUSTOMER_ORDERS", "View Customer Orders", "View customer order history", "CUSTOMER", customerManage, 4)
        ));

        // 4. PRODUCT MANAGEMENT PERMISSIONS
        UserPermission productManage = createParentPermission("PRODUCT_MANAGE", "Product Management",
                "Manage products and catalog", "PRODUCT", 4);
        productManage = userPermissionRepository.save(productManage); // ✅ Save and get ID

        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("CREATE_PRODUCT", "Create Product", "Add new products", "PRODUCT", productManage, 1),
                createSubPermission("EDIT_PRODUCT", "Edit Product", "Edit product details", "PRODUCT", productManage, 2),
                createSubPermission("DELETE_PRODUCT", "Delete Product", "Delete products", "PRODUCT", productManage, 3),
                createSubPermission("VIEW_PRODUCT", "View Product", "View product details", "PRODUCT", productManage, 4)
        ));

        // 5. ORDER MANAGEMENT PERMISSIONS
        UserPermission orderManage = createParentPermission("ORDER_MANAGE", "Order Management",
                "Manage customer orders", "ORDER", 5);
        orderManage = userPermissionRepository.save(orderManage); // ✅ Save and get ID

        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("VIEW_ORDER", "View Order", "View order details", "ORDER", orderManage, 1),
                createSubPermission("UPDATE_ORDER_STATUS", "Update Order Status", "Change order status", "ORDER", orderManage, 2),
                createSubPermission("CANCEL_ORDER", "Cancel Order", "Cancel orders", "ORDER", orderManage, 3),
                createSubPermission("PROCESS_REFUND", "Process Refund", "Process order refunds", "ORDER", orderManage, 4)
        ));

        // 6. INVENTORY MANAGEMENT PERMISSIONS
        UserPermission inventoryManage = createParentPermission("INVENTORY_MANAGE", "Inventory Management",
                "Manage stock and inventory", "INVENTORY", 6);
        inventoryManage = userPermissionRepository.save(inventoryManage); // ✅ Save and get ID

        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("VIEW_INVENTORY", "View Inventory", "View inventory levels", "INVENTORY", inventoryManage, 1),
                createSubPermission("UPDATE_INVENTORY", "Update Inventory", "Update stock levels", "INVENTORY", inventoryManage, 2),
                createSubPermission("INVENTORY_AUDIT", "Inventory Audit", "Perform inventory audits", "INVENTORY", inventoryManage, 3)
        ));

        // 7. REPORTS & ANALYTICS PERMISSIONS
        UserPermission reportsManage = createParentPermission("REPORTS_MANAGE", "Reports & Analytics",
                "View reports and analytics", "REPORTING", 7);
        reportsManage = userPermissionRepository.save(reportsManage); // ✅ Save and get ID

        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("VIEW_SALES_REPORT", "View Sales Report", "View sales analytics", "REPORTING", reportsManage, 1),
                createSubPermission("VIEW_INVENTORY_REPORT", "View Inventory Report", "View inventory reports", "REPORTING", reportsManage, 2),
                createSubPermission("VIEW_CUSTOMER_REPORT", "View Customer Report", "View customer analytics", "REPORTING", reportsManage, 3),
                createSubPermission("EXPORT_REPORTS", "Export Reports", "Export reports to Excel/PDF", "REPORTING", reportsManage, 4)
        ));

        // 8. POS MANAGEMENT PERMISSIONS
        UserPermission posManage = createParentPermission("MANAGE_POS", "POS Management",
                "Manage point of sale operations", "POS", 8);
        posManage = userPermissionRepository.save(posManage); // ✅ Save and get ID
        log.info("✅ Created parent permission: MANAGE_POS with ID: {}", posManage.getId());
        userPermissionRepository.saveAll(Arrays.asList(
                createSubPermission("VIEW_POS", "View POS", "View POS details and transactions", "POS", posManage, 1),
                createSubPermission("EDIT_POS", "Edit POS", "Edit POS configurations", "POS", posManage, 2)
        ));

        log.info("✅ Permissions initialized successfully");
    }

    private UserPermission createParentPermission(String code, String name, String description, String module, int order) {
        //check permission is already exists //ignore and continue
        Optional<UserPermission> existing = userPermissionRepository.findByPermissionCode(code);
        if (existing.isPresent()) {
            log.warn("Permission already exists: {}. Skipping creation.", code);
            return existing.get();
        }
        return UserPermission.builder()
                .permissionCode(code)
                .permissionName(name)
                .description(description)
                .module(module)
                .isParent(true)
                .displayOrder(order)
                .build();
    }

    private UserPermission createSubPermission(String code, String name, String description, String module,
                                               UserPermission parent, int order) {
        //check permission is already exists //ignore and continue
        Optional<UserPermission> existing = userPermissionRepository.findByPermissionCode(code);
        if (existing.isPresent()) {
            log.warn("Permission already exists: {}. Skipping creation.", code);
            return existing.get();
        }
        return UserPermission.builder()
                .permissionCode(code)
                .permissionName(name)
                .description(description)
                .module(module)
                .parent(parent)
                .isParent(false)
                .displayOrder(order)
                .build();
    }

    private void assignPermissionsToRole(UserRole role, List<String> permissionCodes) {
        log.info("Assigning {} permissions to role: {}", permissionCodes.size(), role.getRoleCode());
        final AtomicInteger assignedCount = new AtomicInteger(0);
        final AtomicInteger notFoundCount = new AtomicInteger(0);

        for (String code : permissionCodes) {
            userPermissionRepository.findByPermissionCode(code).ifPresentOrElse(
                    permission -> {
                        RolePermission rolePermission = RolePermission.builder()
                                .userRole(role)
                                .permission(permission)
                                .build();
                        rolePermissionRepository.save(rolePermission);
                        log.debug("✅ Assigned permission {} to role {}", code, role.getRoleCode());
                        assignedCount.incrementAndGet();
                    },
                    () -> {
                        log.warn("❌ Permission not found: {} for role {}", code, role.getRoleCode());
                        notFoundCount.incrementAndGet()
                        ;
                    }
            );
        }

        // Verify assignments
        long finalCount = rolePermissionRepository.findByRoleId(role.getId()).size();
        log.info("✅ Role {}: assigned {}/{} permissions (total: {})",
                role.getRoleCode(), assignedCount.get(), permissionCodes.size(), finalCount);
    }
}
