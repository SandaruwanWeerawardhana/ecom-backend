package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Event to assign permissions to role
 * Published by: Admin Module
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignPermissionsToRoleEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;
    private String roleCode;
    private List<String> permissionCodes;
    private String assignedBy;
}

