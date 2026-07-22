package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for shipment creation (from Order module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentCreationRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long orderId;
    private Long courierId;
    private BigDecimal shipmentWeight;
    private BigDecimal shippingCost;
    private Map<String, Object> shippingBreakdown;
    private Long paymentMethodId;
    private String payerType; // CUSTOMER or RESELLER
}

