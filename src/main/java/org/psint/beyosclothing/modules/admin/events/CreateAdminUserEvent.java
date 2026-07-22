package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event to create admin user in Auth DB
 * Published by: Admin Module
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminUserEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String password; // Already encrypted
    private String roleCode;
    private String createdBy;
    private LocalDateTime createdAt;
}

