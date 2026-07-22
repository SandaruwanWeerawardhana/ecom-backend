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
public class PaymentRequestCreationRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long orderId;
    private String orderUuid;
    private String orderNumber; // Actual order number (e.g., BYS-20260121-0001)
    private Long methodId;
    private BigDecimal amount;
    private String currency;
    private String returnUrl;
    private String cancelUrl;
    private String customerFirstName;
    private String customerLastName;
    private String customerEmail;
    private String customerPhone;
}
