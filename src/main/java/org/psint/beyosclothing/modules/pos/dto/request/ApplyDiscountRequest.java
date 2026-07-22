package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apply discount to cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyDiscountRequest {

    @NotNull(message = "discountAmount is required")
    @Min(value = 0, message = "discountAmount must be >= 0")
    private Double discountAmount;
}
