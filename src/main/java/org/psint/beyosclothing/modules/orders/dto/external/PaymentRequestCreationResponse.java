package org.psint.beyosclothing.modules.orders.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestCreationResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean success;
    private Long paymentRequestId;
    private String paymentRequestUuid;
    private String status; // PENDING, REDIRECTED
    private String redirectUrl; // For online gateways
    private String errorMessage;
}

