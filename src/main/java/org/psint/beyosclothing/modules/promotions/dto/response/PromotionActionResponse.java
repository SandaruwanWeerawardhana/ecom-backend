package org.psint.beyosclothing.modules.promotions.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Promotion Action Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionActionResponse {

    private String uuid;
    private String actionType;
    private Long productId;
    private Long categoryId;
    private BigDecimal discountValue;
    private Long freeProductId;
    private Integer freeQuantity;
}

