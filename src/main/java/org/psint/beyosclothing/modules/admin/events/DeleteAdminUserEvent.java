package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event to delete/rollback admin user creation in Auth DB
 * Published by: Admin Module (for compensation/rollback)
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAdminUserEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String reason;
    private String deletedBy;
    private LocalDateTime deletedAt;
}

