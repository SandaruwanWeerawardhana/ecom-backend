package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for Admin Update Reseller Settings Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin request to update reseller settings")
public class AdminUpdateResellerSettingsRequest {

    @Schema(description = "Allow price override", example = "true")
    private Boolean allowPriceOverride;

    @DecimalMin(value = "0.0", message = "Minimum markup percentage must be non-negative")
    @Schema(description = "Minimum allowed markup percentage", example = "10.00")
    private BigDecimal minAllowedMarkupPct;

    @DecimalMin(value = "0.0", message = "Maximum markup percentage must be non-negative")
    @Schema(description = "Maximum allowed markup percentage", example = "50.00")
    private BigDecimal maxAllowedMarkupPct;

    @DecimalMin(value = "0.0", message = "Credit limit must be non-negative")
    @Schema(description = "Credit limit", example = "50000.00")
    private BigDecimal creditLimit;
}

