package org.psint.beyosclothing.modules.payment.dto.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payment Gateway Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayRequest {
    private Long orderId;
    private String orderNumber;
    private BigDecimal amount;
    private String currency;
    private String customerEmail;
    private String customerName;
    private String customerFirstName;
    private String customerLastName;
    private String customerPhone;
    private String returnUrl;
    private String cancelUrl;
    private String notifyUrl;
    private String orderUuid;
    private String paymentRequestUuid;
}

