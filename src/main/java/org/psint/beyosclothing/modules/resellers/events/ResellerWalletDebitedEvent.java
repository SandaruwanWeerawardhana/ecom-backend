package org.psint.beyosclothing.modules.resellers.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when balance is deducted from reseller wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerWalletDebitedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String resellerUuid;
    private String transactionUuid;
    private BigDecimal amount;
    private String reason;
    private BigDecimal balanceAfter;
    private LocalDateTime timestamp;
}

