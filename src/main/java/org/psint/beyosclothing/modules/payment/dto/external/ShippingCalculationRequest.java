package org.psint.beyosclothing.modules.payment.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Request DTO for calculating shipping cost
 * Sent to Delivery module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCalculationRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private String courierUuid;
    private BigDecimal totalWeight; // in kg
    private String customerType; // "CUSTOMER" or "RESELLER"
    private Long paymentMethodId;
}

