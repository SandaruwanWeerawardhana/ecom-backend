package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Withdrawal List Response (Paginated)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of withdrawal requests")
public class WithdrawalListResponse {

    @Schema(description = "List of withdrawals")
    private List<WithdrawalSummary> withdrawals;

    @Schema(description = "Current page number")
    private Integer currentPage;

    @Schema(description = "Total pages")
    private Integer totalPages;

    @Schema(description = "Total withdrawals")
    private Long totalWithdrawals;

    @Schema(description = "Page size")
    private Integer pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawalSummary {
        @Schema(description = "Withdrawal UUID")
        private String uuid;

        @Schema(description = "Amount")
        private BigDecimal amount;

        @Schema(description = "Status")
        private String status;

        @Schema(description = "Requested date")
        private LocalDateTime requestedDate;

        @Schema(description = "Bank name")
        private String bankName;

        @Schema(description = "Masked account number (e.g. ****7890)")
        private String accountNumber;

        @Schema(description = "Account holder name")
        private String accountHolderName;
    }
}
