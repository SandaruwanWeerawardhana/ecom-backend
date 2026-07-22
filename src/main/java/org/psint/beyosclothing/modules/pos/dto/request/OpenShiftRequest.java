package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to open a POS shift
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenShiftRequest {

    @NotNull(message = "cashierId is required")
    private Long cashierId; // matches PosShiftEntity.cashierId

    @NotNull(message = "terminalId is required")
    private Long terminalId; // matches PosShiftEntity.terminalId

    @NotNull(message = "openingBalance is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "openingBalance must be >= 0")
    private BigDecimal openingBalance;
}
