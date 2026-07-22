package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Response DTO for courier lookup via RabbitMQ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierLookupResponse implements Serializable {
    private Boolean found;
    private Long courierId;
    private String courierUuid;
    private String courierName;
    private String courierCode;
    private Boolean isActive;
    private BigDecimal shippingCost;  // Default shipping cost if available
    private String errorMessage;
}

