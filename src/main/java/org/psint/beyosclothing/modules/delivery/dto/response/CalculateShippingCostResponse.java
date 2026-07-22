package org.psint.beyosclothing.modules.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for shipping cost calculation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalculateShippingCostResponse {

    private BigDecimal shippingCost;
    private BigDecimal totalWeight;
    private Boolean isFree;
    private String freeReason;
    private Map<String, Object> breakdown;
    private String courierName;
    private String courierUuid;
    private Long courierRateId;
}

