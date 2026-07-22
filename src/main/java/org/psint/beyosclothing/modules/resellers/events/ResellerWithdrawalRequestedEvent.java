package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when reseller requests withdrawal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerWithdrawalRequestedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String withdrawalUuid;
    private String resellerUuid;
    private BigDecimal amount;
    private String bankAccountUuid;
    private LocalDateTime requestDate;
    private LocalDateTime timestamp;
}

