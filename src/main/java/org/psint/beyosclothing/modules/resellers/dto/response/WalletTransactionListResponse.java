package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Wallet Transaction List Response (Paginated)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of wallet transactions")
public class WalletTransactionListResponse {

    @Schema(description = "List of transactions")
    private List<TransactionSummary> transactions;

    @Schema(description = "Current page number")
    private Integer currentPage;

    @Schema(description = "Total pages")
    private Integer totalPages;

    @Schema(description = "Total transactions")
    private Long totalTransactions;

    @Schema(description = "Page size")
    private Integer pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummary {
        @Schema(description = "Transaction UUID")
        private String uuid;

        @Schema(description = "Transaction type")
        private String type;

        @Schema(description = "Amount")
        private BigDecimal amount;

        @Schema(description = "Date")
        private LocalDateTime date;

        @Schema(description = "Is credit")
        private Boolean isCredit;

        @Schema(description = "Reference info")
        private String referenceInfo;
    }
}

