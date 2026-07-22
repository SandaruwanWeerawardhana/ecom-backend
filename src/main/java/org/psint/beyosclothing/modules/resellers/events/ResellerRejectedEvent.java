package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when admin rejects a reseller application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerRejectedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String resellerUuid;
    private Long userId;
    private String email;
    private String rejectionReason;
    private LocalDateTime rejectionDate;
    private LocalDateTime timestamp;
}

