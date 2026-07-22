package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for Wallet Balance Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reseller wallet balance information")
public class WalletBalanceResponse {

    @Schema(description = "Current balance", example = "15000.00")
    private BigDecimal currentBalance;

    @Schema(description = "Credit limit", example = "50000.00")
    private BigDecimal creditLimit;

    @Schema(description = "Available credit", example = "35000.00")
    private BigDecimal availableCredit;

    @Schema(description = "Total earned (lifetime)", example = "125000.00")
    private BigDecimal totalEarned;

    @Schema(description = "Total withdrawn (lifetime)", example = "110000.00")
    private BigDecimal totalWithdrawn;

    @Schema(description = "Pending withdrawals amount", example = "5000.00")
    private BigDecimal pendingWithdrawals;
}

