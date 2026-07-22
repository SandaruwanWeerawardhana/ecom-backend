package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event to deactivate admin user in Auth DB (set isActive = false on User)
 * Published by: Admin Module (when admin is deactivated)
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateAdminUserEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String deactivatedBy;
    private LocalDateTime deactivatedAt;
}
