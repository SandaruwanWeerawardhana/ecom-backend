package org.psint.beyosclothing.modules.payment.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for payment method lookup (from Order module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodLookupRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long paymentMethodId;
}

