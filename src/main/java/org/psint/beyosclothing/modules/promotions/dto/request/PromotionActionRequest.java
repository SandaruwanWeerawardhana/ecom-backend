package org.psint.beyosclothing.modules.promotions.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Promotion Action Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionActionRequest {

    @NotBlank(message = "Action type is required")
    private String actionType; // APPLY_PERCENTAGE_DISCOUNT, APPLY_FIXED_DISCOUNT, etc.

    private Long productId;

    private Long categoryId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be positive")
    private BigDecimal discountValue;

    private Long freeProductId;

    @Min(value = 1, message = "Free quantity must be at least 1")
    private Integer freeQuantity;
}

