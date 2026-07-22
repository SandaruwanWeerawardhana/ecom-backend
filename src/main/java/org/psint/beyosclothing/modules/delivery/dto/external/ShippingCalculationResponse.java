package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for shipping cost calculation
 * Sent back to Payment module via RabbitMQ
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
    private Boolean isFree; // If payment method grants free shipping
    private Map<String, Object> breakdown; // { "first_kg": 200.00, "additional": 160.00, "total": 360.00 }
    private String errorMessage;
}

