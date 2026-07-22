package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Shared Response DTO for shipping cost calculation
 * Used by Delivery module (sender/replier) and Payment module (receiver) via RabbitMQ
 * MUST be in shared package to avoid __TypeId__ class mismatch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCalculationResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long courierId;
    private String courierName;
    private BigDecimal shippingCost;
    private BigDecimal totalWeight;
    private Boolean isFree;
    private Map<String, Object> breakdown;
    private String errorMessage;
}
