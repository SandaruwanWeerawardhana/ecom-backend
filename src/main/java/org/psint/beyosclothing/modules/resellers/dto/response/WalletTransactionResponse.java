package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Wallet Transaction Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wallet transaction details")
public class WalletTransactionResponse {

    @Schema(description = "Transaction UUID")
    private String uuid;

    @Schema(description = "Transaction type")
    private String type;

    @Schema(description = "Amount")
    private BigDecimal amount;

    @Schema(description = "Balance before transaction")
    private BigDecimal balanceBefore;

    @Schema(description = "Balance after transaction")
    private BigDecimal balanceAfter;

    @Schema(description = "Transaction date")
    private LocalDateTime date;

    @Schema(description = "Reference information (order number or withdrawal ID)")
    private String referenceInfo;

    @Schema(description = "Notes")
    private String notes;

    @Schema(description = "Is credit transaction")
    private Boolean isCredit;
}

