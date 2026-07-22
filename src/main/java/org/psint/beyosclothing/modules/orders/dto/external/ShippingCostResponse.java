package org.psint.beyosclothing.modules.orders.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCostResponse implements Serializable {
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

