package org.psint.beyosclothing.modules.promotions.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Promotion Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionResponse {

    private String uuid;
    private String name;
    private String description;
    private String promoCode;
    private Boolean isCodeRequired;
    private String discountType;
    private BigDecimal discountValue;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isStackable;
    private Integer usageLimit;
    private Integer usageLimitPerUser;
    private Long currentUsageCount;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    private List<PromotionConditionResponse> conditions;
    private List<PromotionActionResponse> actions;
    private List<Long> productIds;
    private List<Long> categoryIds;
}

