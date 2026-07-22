package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apply tax to cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyTaxRequest {

    @NotNull(message = "taxPercentage is required")
    @Min(value = 0, message = "taxPercentage must be >= 0")
    @Max(value = 100, message = "taxPercentage must be <= 100")
    private Double taxPercentage;
}
