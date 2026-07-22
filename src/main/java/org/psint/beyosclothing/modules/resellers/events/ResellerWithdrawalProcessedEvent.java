package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when admin processes withdrawal request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerWithdrawalProcessedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String withdrawalUuid;
    private String resellerUuid;
    private String status; // APPROVED or REJECTED
    private BigDecimal amount;
    private Long processedBy;
    private LocalDateTime processedDate;
    private LocalDateTime timestamp;
}

