package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event to create admin role
 * Published by: Admin Module
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminRoleEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roleCode;
    private String roleName;
    private String description;
    private String userType; // Always "ADMIN"
    private List<String> permissionCodes; // Permission codes to assign
    private LocalDateTime createdAt;
    private String createdBy;
}

