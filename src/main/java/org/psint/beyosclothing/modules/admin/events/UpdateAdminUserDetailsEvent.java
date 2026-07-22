package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event to update admin user details (email, etc.) in Auth DB
 * Published by: Admin Module
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdminUserDetailsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String email;
    private String updatedBy;
    private LocalDateTime updatedAt;
}

