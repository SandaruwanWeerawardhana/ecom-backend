package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Wallet Full Summary Response
 * Combines wallet balance with payment module transaction data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reseller wallet full summary with earnings, withdrawals, and recent transactions")
public class WalletFullSummaryResponse {

    @Schema(description = "Current available balance", example = "3240.50")
    private BigDecimal availableBalance;

    @Schema(description = "Total earnings (lifetime)", example = "12450.00")
    private BigDecimal totalEarnings;

    @Schema(description = "Pending withdrawals amount", example = "500.00")
    private BigDecimal pendingWithdrawals;

    @Schema(description = "Total withdrawn (lifetime)", example = "8709.50")
    private BigDecimal totalWithdrawn;

    @Schema(description = "List of recent transactions (up to 10 most recent)")
    private List<TransactionSummary> recentTransactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary of a wallet transaction")
    public static class TransactionSummary {

        @Schema(description = "Transaction type (Credit/Debit)", example = "Credit")
        private String type;

        @Schema(description = "Transaction description", example = "Order #ORD-001 Payment")
        private String description;

        @Schema(description = "Transaction amount", example = "125.00")
        private BigDecimal amount;

        @Schema(description = "Transaction date", example = "2024-01-24")
        private String date;

        @Schema(description = "Transaction status", example = "Completed")
        private String status;
    }
}

