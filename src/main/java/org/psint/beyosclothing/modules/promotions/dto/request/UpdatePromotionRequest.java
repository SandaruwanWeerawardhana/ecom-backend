package org.psint.beyosclothing.modules.promotions.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Update Promotion Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePromotionRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @Size(max = 50, message = "Promo code must not exceed 50 characters")
    private String promoCode;

    private Boolean isCodeRequired;

    private String discountType;

    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be positive")
    private BigDecimal discountValue;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Boolean isStackable;

    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;

    @Min(value = 1, message = "Usage limit per user must be at least 1")
    private Integer usageLimitPerUser;

    @Valid
    private List<PromotionConditionRequest> conditions;

    @Valid
    private List<PromotionActionRequest> actions;

    private List<Long> productIds;

    private List<Long> categoryIds;
}

