package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for looking up payment method from Payment module via RabbitMQ
 * Used by Delivery module to get payment method details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodLookupRequest implements Serializable {

    private String requestId;

    // Can lookup by either ID or UUID
    private Long paymentMethodId;
    private String paymentMethodUuid;
    private String paymentMethodCode;
}

