package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to close a POS shift
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseShiftRequest {

    @NotBlank(message = "uuid is required")
    private String uuid; // matches PosShiftEntity.uuid

    @NotNull(message = "closingBalance is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "closingBalance must be >= 0")
    private BigDecimal closingBalance;

    private String notes;
}
