package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for admin wallet adjustments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to adjust reseller wallet balance")
public class AdminWalletAdjustmentRequest {

    @NotNull(message = "Amount is required")
    @Schema(description = "Amount to add (positive) or deduct (negative)", example = "5000.00", required = true)
    private BigDecimal amount;

    @NotNull(message = "Reason is required")
    @Schema(description = "Reason for adjustment", example = "Compensation for order issue", required = true)
    private String reason;
}

