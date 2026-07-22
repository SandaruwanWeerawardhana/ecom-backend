package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for payment method lookup from Payment module via RabbitMQ
 * Returns payment method details to Delivery module
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodLookupResponse implements Serializable {

    private String requestId;
    private boolean found;
    private String errorMessage;

    // Payment Method Details
    private Long paymentMethodId;
    private String paymentMethodUuid;
    private String paymentMethodCode;
    private String paymentMethodName;
    private String paymentMethodType;
    private Boolean isActive;
    private Boolean isCourierFeeFree;
    private Boolean supportsRefund;
    private Boolean supportsCallback;
}

