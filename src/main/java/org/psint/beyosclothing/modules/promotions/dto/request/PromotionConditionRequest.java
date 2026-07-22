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
 * Promotion Condition Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionConditionRequest {

    private String description;

    @NotBlank(message = "Condition type is required")
    private String conditionType; // MIN_CART_TOTAL, MIN_ITEM_QUANTITY, etc.

    private Long productId;

    private Long categoryId;

    @Min(value = 1, message = "Minimum quantity must be at least 1")
    private Integer minQuantity;

    @DecimalMin(value = "0.0", inclusive = false, message = "Minimum amount must be positive")
    private BigDecimal minAmount;
}

