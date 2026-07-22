package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when admin suspends a reseller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerSuspendedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String resellerUuid;
    private Long userId;
    private String suspensionReason;
    private LocalDateTime suspensionDate;
    private LocalDateTime timestamp;
}


