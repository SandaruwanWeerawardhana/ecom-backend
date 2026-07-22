package org.psint.beyosclothing.modules.promotions.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Promotion Condition Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionConditionResponse {

    private String uuid;
    private String description;
    private String conditionType;
    private Long productId;
    private Long categoryId;
    private Integer minQuantity;
    private BigDecimal minAmount;
}

