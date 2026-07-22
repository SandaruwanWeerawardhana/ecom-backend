package org.psint.beyosclothing.modules.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.events.CreateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.InitializePermissionsEvent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Admin Data Initialization Service
 * Initializes default permissions and admin roles on application startup via events
 * Note: Uses event-based communication with Auth module (no direct repository access)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDataInitializationService implements CommandLineRunner {

    private final AdminEventPublisherService eventPublisher;

    @Override
    public void run(String... args) {
        log.info("🚀 Initializing Admin Module Data via Events...");

        initializePermissions();
        initializeAdminRoles();

        log.info("✅ Admin Module Data Initialization Events Published!");
    }

    /**
     * Publish event to initialize hierarchical permissions in Auth DB
     */
    private void initializePermissions() {
        log.info("📋 Publishing Permission Initialization Event...");

        InitializePermissionsEvent event = InitializePermissionsEvent.builder()
                .requestId(UUID.randomUUID().toString())
                .requestedAt(LocalDateTime.now())
                .initiatedBy("SYSTEM")
                .build();

        eventPublisher.publishInitializePermissionsEvent(event);
    }

    /**
     * Publish events to create default admin roles in Auth DB
     */
    private void initializeAdminRoles() {
        log.info("👥 Publishing Admin Role Creation Events...");

        // 1. SUPER_ADMIN - Full access to everything
        CreateAdminRoleEvent superAdmin = CreateAdminRoleEvent.builder()
                .roleCode("SUPER_ADMIN")
                .roleName("Super Administrator")
                .description("Full system access with all permissions")
                .userType("ADMIN")
                .permissionCodes(getAllPermissionCodes())
                .createdAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();
        eventPublisher.publishCreateAdminRoleEvent(superAdmin);

        // 2. MANAGER - Management-level permissions
        CreateAdminRoleEvent manager = CreateAdminRoleEvent.builder()
                .roleCode("MANAGER")
                .roleName("Manager")
                .description("Store manager with elevated permissions")
                .userType("ADMIN")
                .permissionCodes(Arrays.asList(
                    "VIEW_ADMIN", "VIEW_CUSTOMER", "EDIT_CUSTOMER", "VIEW_CUSTOMER_ORDERS",
                    "CREATE_PRODUCT", "EDIT_PRODUCT", "VIEW_PRODUCT",
                    "VIEW_ORDER", "UPDATE_ORDER_STATUS", "CANCEL_ORDER", "PROCESS_REFUND",
                    "VIEW_INVENTORY", "UPDATE_INVENTORY",
                    "VIEW_SALES_REPORT", "VIEW_INVENTORY_REPORT", "VIEW_CUSTOMER_REPORT", "EXPORT_REPORTS"
                ))
                .createdAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();
        eventPublisher.publishCreateAdminRoleEvent(manager);

        // 3. CASHIER - POS and order processing
        CreateAdminRoleEvent cashier = CreateAdminRoleEvent.builder()
                .roleCode("CASHIER")
                .roleName("Cashier")
                .description("Point of sale and order processing")
                .userType("ADMIN")
                .permissionCodes(Arrays.asList(
                    "VIEW_CUSTOMER", "VIEW_PRODUCT",
                    "VIEW_ORDER", "UPDATE_ORDER_STATUS",
                    "VIEW_INVENTORY"
                ))
                .createdAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();
        eventPublisher.publishCreateAdminRoleEvent(cashier);

        // 4. INVENTORY_OFFICER - Inventory management
        CreateAdminRoleEvent inventoryOfficer = CreateAdminRoleEvent.builder()
                .roleCode("INVENTORY_OFFICER")
                .roleName("Inventory Officer")
                .description("Inventory and stock management")
                .userType("ADMIN")
                .permissionCodes(Arrays.asList(
                    "VIEW_PRODUCT", "EDIT_PRODUCT",
                    "VIEW_INVENTORY", "UPDATE_INVENTORY", "INVENTORY_AUDIT",
                    "VIEW_INVENTORY_REPORT"
                ))
                .createdAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();
        eventPublisher.publishCreateAdminRoleEvent(inventoryOfficer);

        log.info("✅ 4 Admin Role Creation Events Published");
    }

    /**
     * Get all permission codes for SUPER_ADMIN
     */
    private List<String> getAllPermissionCodes() {
        return Arrays.asList(
            // Admin Management
            "ADMIN_MANAGE", "CREATE_ADMIN", "EDIT_ADMIN", "DELETE_ADMIN", "VIEW_ADMIN",
            "EDIT_ADMIN_PASSWORD", "ASSIGN_ADMIN_ROLE",
            // Role Management
            "ROLE_MANAGE", "CREATE_ROLE", "EDIT_ROLE", "DELETE_ROLE", "VIEW_ROLE", "ASSIGN_PERMISSION",
            // Customer Management
            "CUSTOMER_MANAGE", "VIEW_CUSTOMER", "EDIT_CUSTOMER", "BLOCK_CUSTOMER", "VIEW_CUSTOMER_ORDERS",
            // Product Management
            "PRODUCT_MANAGE", "CREATE_PRODUCT", "EDIT_PRODUCT", "DELETE_PRODUCT", "VIEW_PRODUCT",
            // Order Management
            "ORDER_MANAGE", "VIEW_ORDER", "UPDATE_ORDER_STATUS", "CANCEL_ORDER", "PROCESS_REFUND",
            // Inventory Management
            "INVENTORY_MANAGE", "VIEW_INVENTORY", "UPDATE_INVENTORY", "INVENTORY_AUDIT",
            // Reports & Analytics
            "REPORTS_MANAGE", "VIEW_SALES_REPORT", "VIEW_INVENTORY_REPORT", "VIEW_CUSTOMER_REPORT", "EXPORT_REPORTS"
        );
    }
}
