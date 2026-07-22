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
 * Create Promotion Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePromotionRequest {

    @NotBlank(message = "Promotion name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @Size(max = 50, message = "Promo code must not exceed 50 characters")
    private String promoCode;

    @NotNull(message = "is_code_required is required")
    private Boolean isCodeRequired;

    @NotBlank(message = "Discount type is required")
    private String discountType; // PERCENTAGE, FIXED, FREE_PRODUCT, BOGO

    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be positive")
    private BigDecimal discountValue;

    @NotNull(message = "Start date is required")
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @NotNull(message = "is_stackable is required")
    private Boolean isStackable;

    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;

    @Min(value = 1, message = "Usage limit per user must be at least 1")
    private Integer usageLimitPerUser;

    // Conditions
    @Valid
    private List<PromotionConditionRequest> conditions;

    // Actions
    @Valid
    @NotEmpty(message = "At least one action is required")
    private List<PromotionActionRequest> actions;

    // Product mappings (optional - for specific products)
    private List<Long> productIds;

    // Category mappings (optional - for specific categories)
    private List<Long> categoryIds;
}

