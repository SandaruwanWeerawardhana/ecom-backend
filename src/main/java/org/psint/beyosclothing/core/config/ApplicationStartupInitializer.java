package org.psint.beyosclothing.core.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.events.CreateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.CreateAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.InitializePermissionsEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Application Startup Initializer
 * Handles one-time initialization tasks when the application starts
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationStartupInitializer {

    private final RabbitTemplate rabbitTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.rabbitmq.exchange.admin}")
    private String adminExchange;

    @Value("${app.rabbitmq.routing-key.admin-permissions-initialize}")
    private String permissionsInitializeRoutingKey;

    @Value("${app.rabbitmq.routing-key.admin-role-create}")
    private String roleCreateRoutingKey;

    @Value("${app.rabbitmq.routing-key.admin-user-create}")
    private String adminUserCreateRoutingKey;

    /**
     * Triggered when the application is fully started and ready to serve requests
     * This runs AFTER all beans are initialized and all configurations are loaded
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Application is ready. Running startup initializations...");

        // Step 1: Initialize permissions first
        initializePermissions();

        // Step 2: Wait a moment for permissions to be created
        try {
            Thread.sleep(2000); // Wait 2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 3: Create default admin roles
        createDefaultAdminRoles();

        // Step 4: Wait for roles to be created
        try {
            Thread.sleep(2000); // Wait 2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 5: Create default super admin user
        createDefaultSuperAdminUser();

        log.info("✅ Startup initializations completed");
    }

    /**
     * Initialize hierarchical permissions on application startup
     */
    private void initializePermissions() {
        try {
            log.info("🔧 Triggering permission initialization on startup...");

            InitializePermissionsEvent event = InitializePermissionsEvent.builder()
                    .requestId("startup-permissions-" + UUID.randomUUID().toString())
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, permissionsInitializeRoutingKey, event);

            log.info("✅ Permission initialization event published successfully");

        } catch (Exception e) {
            log.error("❌ Error triggering permission initialization: {}", e.getMessage(), e);
        }
    }

    /**
     * Create default admin roles on application startup
     */
    private void createDefaultAdminRoles() {
        try {
            log.info("🔧 Creating default admin roles on startup...");

            // Create SUPER_ADMIN role with all permissions
            CreateAdminRoleEvent superAdminEvent = CreateAdminRoleEvent.builder()
                    .roleCode("SUPER_ADMIN")
                    .roleName("Super Administrator")
                    .description("Full system access with all permissions")
                    .userType("ADMIN")
                    .permissionCodes(Arrays.asList(
                        // Admin Management
                        "CREATE_ADMIN", "EDIT_ADMIN", "DELETE_ADMIN", "VIEW_ADMIN",
                        "EDIT_ADMIN_PASSWORD", "ASSIGN_ADMIN_ROLE",
                        // Role Management
                        "CREATE_ROLE", "EDIT_ROLE", "DELETE_ROLE", "VIEW_ROLE", "ASSIGN_PERMISSION",
                        // Customer Management
                        "VIEW_CUSTOMER", "EDIT_CUSTOMER", "BLOCK_CUSTOMER", "VIEW_CUSTOMER_ORDERS",
                        // Product Management
                        "CREATE_PRODUCT", "EDIT_PRODUCT", "DELETE_PRODUCT", "VIEW_PRODUCT",
                        // Order Management
                        "VIEW_ORDER", "UPDATE_ORDER_STATUS", "CANCEL_ORDER", "PROCESS_REFUND",
                        // Inventory Management
                        "VIEW_INVENTORY", "UPDATE_INVENTORY", "INVENTORY_AUDIT",
                        // Reports & Analytics
                        "VIEW_SALES_REPORT", "VIEW_INVENTORY_REPORT", "VIEW_CUSTOMER_REPORT", "EXPORT_REPORTS"
                    ))
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, roleCreateRoutingKey, superAdminEvent);
            log.info("✅ SUPER_ADMIN role creation event published");

            // Create ADMIN_MANAGER role with admin and role management permissions
            CreateAdminRoleEvent adminManagerEvent = CreateAdminRoleEvent.builder()
                    .roleCode("ADMIN_MANAGER")
                    .roleName("Admin Manager")
                    .description("Can manage admin staff and roles")
                    .userType("ADMIN")
                    .permissionCodes(Arrays.asList(
                        "CREATE_ADMIN", "EDIT_ADMIN", "DELETE_ADMIN", "VIEW_ADMIN",
                        "ASSIGN_ADMIN_ROLE", "VIEW_ROLE"
                    ))
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, roleCreateRoutingKey, adminManagerEvent);
            log.info("✅ ADMIN_MANAGER role creation event published");

            // Create CUSTOMER_SERVICE role
            CreateAdminRoleEvent customerServiceEvent = CreateAdminRoleEvent.builder()
                    .roleCode("CUSTOMER_SERVICE")
                    .roleName("Customer Service")
                    .description("Can manage customers and orders")
                    .userType("ADMIN")
                    .permissionCodes(Arrays.asList(
                        "VIEW_CUSTOMER", "EDIT_CUSTOMER", "VIEW_CUSTOMER_ORDERS",
                        "VIEW_ORDER", "UPDATE_ORDER_STATUS", "CANCEL_ORDER", "PROCESS_REFUND"
                    ))
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, roleCreateRoutingKey, customerServiceEvent);
            log.info("✅ CUSTOMER_SERVICE role creation event published");

            // Create INVENTORY_MANAGER role
            CreateAdminRoleEvent inventoryManagerEvent = CreateAdminRoleEvent.builder()
                    .roleCode("INVENTORY_MANAGER")
                    .roleName("Inventory Manager")
                    .description("Can manage products and inventory")
                    .userType("ADMIN")
                    .permissionCodes(Arrays.asList(
                        "CREATE_PRODUCT", "EDIT_PRODUCT", "DELETE_PRODUCT", "VIEW_PRODUCT",
                        "VIEW_INVENTORY", "UPDATE_INVENTORY", "INVENTORY_AUDIT"
                    ))
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, roleCreateRoutingKey, inventoryManagerEvent);
            log.info("✅ INVENTORY_MANAGER role creation event published");

            // Create ANALYST role (read-only)
            CreateAdminRoleEvent analystEvent = CreateAdminRoleEvent.builder()
                    .roleCode("ANALYST")
                    .roleName("Business Analyst")
                    .description("Can view reports and analytics (read-only)")
                    .userType("ADMIN")
                    .permissionCodes(Arrays.asList(
                        "VIEW_SALES_REPORT", "VIEW_INVENTORY_REPORT",
                        "VIEW_CUSTOMER_REPORT", "EXPORT_REPORTS"
                    ))
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, roleCreateRoutingKey, analystEvent);
            log.info("✅ ANALYST role creation event published");

            log.info("✅ All default admin roles creation events published successfully");

        } catch (Exception e) {
            log.error("❌ Error creating default admin roles: {}", e.getMessage(), e);
        }
    }

    /**
     * Create default super admin user on application startup
     */
    private void createDefaultSuperAdminUser() {
        try {
            log.info("🔧 Creating default super admin user on startup...");

            // Encrypt the password
            String encryptedPassword = passwordEncoder.encode("Akindu@0412");

            // Create the super admin user event
            CreateAdminUserEvent adminUserEvent = CreateAdminUserEvent.builder()
                    .email("akiyaramsith2002@gmail.com")
                    .password(encryptedPassword)
                    .roleCode("SUPER_ADMIN")
                    .createdBy("SYSTEM")
                    .createdAt(LocalDateTime.now())
                    .build();

            rabbitTemplate.convertAndSend(adminExchange, adminUserCreateRoutingKey, adminUserEvent);
            log.info("✅ Default super admin user creation event published for email: akiyaramsith2002@gmail.com");

        } catch (Exception e) {
            log.error("❌ Error creating default super admin user: {}", e.getMessage(), e);
        }
    }
}

