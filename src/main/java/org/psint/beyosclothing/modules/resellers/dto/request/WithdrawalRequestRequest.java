package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for Withdrawal Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to withdraw funds from wallet")
public class WithdrawalRequestRequest {

    @NotBlank(message = "Bank account UUID is required")
    @Schema(description = "Bank account UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String bankAccountUuid;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Schema(description = "Withdrawal amount", example = "5000.00")
    private BigDecimal amount;
}

