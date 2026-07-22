package org.psint.beyosclothing.modules.admin.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event to request permission initialization
 * Published by: Admin Module
 * Consumed by: Auth Module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializePermissionsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private LocalDateTime requestedAt;
    private String initiatedBy;
}

