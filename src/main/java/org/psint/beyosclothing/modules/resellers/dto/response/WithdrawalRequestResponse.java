package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Withdrawal Request Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Withdrawal request details")
public class WithdrawalRequestResponse {

    @Schema(description = "Withdrawal request UUID")
    private String uuid;

    @Schema(description = "Amount requested")
    private BigDecimal amount;

    @Schema(description = "Status")
    private String status;

    @Schema(description = "Bank account details")
    private BankAccountInfo bankAccount;

    @Schema(description = "Requested date")
    private LocalDateTime requestedDate;

    @Schema(description = "Processed date")
    private LocalDateTime processedDate;

    @Schema(description = "Completed date")
    private LocalDateTime completedDate;

    @Schema(description = "Admin notes")
    private String adminNotes;

    @Schema(description = "Rejection reason")
    private String rejectionReason;

    @Schema(description = "Transaction reference")
    private String transactionReference;

    @Schema(description = "Balance after withdrawal")
    private BigDecimal balanceAfter;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAccountInfo {
        @Schema(description = "Bank name")
        private String bankName;

        @Schema(description = "Masked account number")
        private String maskedAccountNumber;

        @Schema(description = "Account holder name")
        private String accountHolderName;
    }
}

