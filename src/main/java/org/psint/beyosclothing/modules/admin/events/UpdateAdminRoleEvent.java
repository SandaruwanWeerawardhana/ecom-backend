package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event to update admin user role in Auth DB
 * Published by: Admin Module
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdminRoleEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String roleCode;
    private String updatedBy;
}

