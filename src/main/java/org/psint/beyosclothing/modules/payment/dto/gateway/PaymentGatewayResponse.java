package org.psint.beyosclothing.modules.payment.dto.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payment Gateway Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResponse {
    private Boolean success;
    private String transactionId;
    private String redirectUrl;
    private String status; // PENDING, PAID, FAILED
    private BigDecimal amount;
    private String errorMessage;
    private String errorCode;
}

