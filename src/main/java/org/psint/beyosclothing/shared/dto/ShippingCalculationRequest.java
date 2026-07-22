package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Shared Request DTO for shipping cost calculation
 * Used by Payment module (sender) and Delivery module (receiver) via RabbitMQ
 * MUST be in shared package to avoid __TypeId__ class mismatch
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
