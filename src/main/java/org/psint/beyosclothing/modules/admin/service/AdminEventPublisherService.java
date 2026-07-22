package org.psint.beyosclothing.modules.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.admin.events.AssignPermissionsToRoleEvent;
import org.psint.beyosclothing.modules.admin.events.CreateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.CreateAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.DeactivateAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.DeleteAdminUserEvent;
import org.psint.beyosclothing.modules.admin.events.InitializePermissionsEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminPasswordEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminRoleEvent;
import org.psint.beyosclothing.modules.admin.events.UpdateAdminUserDetailsEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Admin Event Publisher Service
 * Publishes events to Auth module via RabbitMQ
 * Note: Admin module cannot directly access Auth repositories (cross-database)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminEventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.admin:admin.exchange}")
    private String adminExchange;

    /**
     * Publish event to initialize permissions in Auth DB
     */
    public void publishInitializePermissionsEvent(InitializePermissionsEvent event) {
        log.info("Publishing InitializePermissionsEvent: {}", event.getRequestId());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.permissions.initialize",
                event
        );
    }

    /**
     * Publish event to create admin role in Auth DB
     */
    public void publishCreateAdminRoleEvent(CreateAdminRoleEvent event) {
        log.info("Publishing CreateAdminRoleEvent: {}", event.getRoleCode());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.role.create",
                event
        );
    }

    /**
     * Publish event to assign permissions to role in Auth DB
     */
    public void publishAssignPermissionsEvent(AssignPermissionsToRoleEvent event) {
        log.info("Publishing AssignPermissionsToRoleEvent for role: {}", event.getRoleCode());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.permissions.assign",
                event
        );
    }

    /**
     * Publish event to create admin user in Auth DB
     */
    public void publishCreateAdminUserEvent(CreateAdminUserEvent event) {
        log.info("Publishing CreateAdminUserEvent for email: {}", event.getEmail());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.user.create",
                event
        );
    }

    /**
     * Publish event to update admin user role in Auth DB
     */
    public void publishUpdateAdminRoleEvent(UpdateAdminRoleEvent event) {
        log.info("Publishing UpdateAdminRoleEvent for userId: {}", event.getUserId());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.user.role.update",
                event
        );
    }

    /**
     * Publish event to update admin user password in Auth DB
     */
    public void publishUpdateAdminPasswordEvent(UpdateAdminPasswordEvent event) {
        log.info("Publishing UpdateAdminPasswordEvent for userId: {}", event.getUserId());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.user.password.update",
                event
        );
    }

    /**
     * Publish event to update admin user details (email) in Auth DB
     */
    public void publishUpdateAdminUserDetailsEvent(UpdateAdminUserDetailsEvent event) {
        log.info("Publishing UpdateAdminUserDetailsEvent for userId: {}", event.getUserId());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.user.details.update",
                event
        );
    }

    /**
     * Publish event to deactivate admin user in Auth DB (set isActive = false on User)
     */
    public void publishDeactivateAdminUserEvent(DeactivateAdminUserEvent event) {
        log.info("Publishing DeactivateAdminUserEvent for userId: {}", event.getUserId());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.user.deactivate",
                event
        );
    }

    /**
     * Publish event to delete/rollback admin user in Auth DB (for compensation)
     */
    public void publishDeleteAdminUserEvent(DeleteAdminUserEvent event) {
        log.info("Publishing DeleteAdminUserEvent for email: {} - Reason: {}", event.getEmail(), event.getReason());
        rabbitTemplate.convertAndSend(
                adminExchange,
                "admin.user.delete",
                event
        );
    }
}
