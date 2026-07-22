package org.psint.beyosclothing.modules.orders.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodLookupResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long methodId;
    private String methodCode;
    private String methodName;
    private String methodType; // ONLINE, OFFLINE, POS
    private String gatewayName;
    private Boolean isActive;
    private BigDecimal feePercentage;
    private BigDecimal feeFixed;
}

